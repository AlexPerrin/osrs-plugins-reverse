#include <EGL/egl.h>
#include <GLES3/gl3.h>
#include <sys/system_properties.h>
#include <algorithm>
#include <chrono>
#include <cstdlib>
#include <atomic>
#include <mutex>
#include <unistd.h>
#include <unordered_map>
#include <vector>

#include "client_thread.h"
#include "game.h"
#include "imgui.h"
#include "jni_bridge.h"
#include "imgui_impl_opengl3.h"
#include "log.h"
#include "overlay.h"

namespace {

// The game swaps from more than one thread/context (loader + main renderer), so
// each EGL context gets its own ImGui context and GL backend objects. ImGui's
// current-context pointer is process-global, hence the mutex.
struct ContextState {
    ImGuiContext* imgui = nullptr;
    GLuint plugin_tex = 0;
    uint64_t plugin_seq = 0;  // frame uploaded into plugin_tex
    std::chrono::steady_clock::time_point last_frame = std::chrono::steady_clock::now();
    uint64_t frames = 0;
};

std::mutex g_mutex;
std::unordered_map<EGLContext, ContextState> g_states;

std::atomic<int> g_screen_w{0}, g_screen_h{0};

// Double buffer for plugin overlay frames. The producer fills `back` without the lock
// (only it touches back), then overlay_publish swaps under the lock.
std::mutex g_frame_mutex;
std::vector<uint8_t> g_back, g_front;
int g_front_w = 0, g_front_h = 0;
uint64_t g_front_seq = 0;

// The bitmap is premultiplied; ImGui's backend blends with SRC_ALPHA.
void premultiplied_blend(const ImDrawList*, const ImDrawCmd*) { glBlendFuncSeparate(GL_ONE, GL_ONE_MINUS_SRC_ALPHA, GL_ONE, GL_ONE_MINUS_SRC_ALPHA); }

// Uploads the newest plugin frame into this context's texture; false if none is shown.
bool update_plugin_texture(ContextState& st) {
    std::lock_guard lock(g_frame_mutex);
    if (g_front_w <= 0) return false;
    if (st.plugin_seq != g_front_seq) {
        if (!st.plugin_tex) {
            glGenTextures(1, &st.plugin_tex);
            glBindTexture(GL_TEXTURE_2D, st.plugin_tex);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
        }
        glBindTexture(GL_TEXTURE_2D, st.plugin_tex);
        glPixelStorei(GL_UNPACK_ALIGNMENT, 4);
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, g_front_w, g_front_h, 0, GL_RGBA, GL_UNSIGNED_BYTE, g_front.data());
        st.plugin_seq = g_front_seq;
    }
    return true;
}

// `setprop debug.osrsrl.overlay 0` hides the debug window (checked about once a second);
// plugin overlays are still drawn.
bool debug_window_enabled() {
    static std::atomic<int64_t> next_check{0};
    static std::atomic<bool> enabled{true};
    int64_t now = std::chrono::steady_clock::now().time_since_epoch().count();
    if (now >= next_check.load(std::memory_order_relaxed)) {
        char value[PROP_VALUE_MAX] = {};
        __system_property_get("debug.osrsrl.overlay", value);
        enabled = value[0] != '0';
        next_check = now + std::chrono::nanoseconds(std::chrono::seconds(1)).count();
    }
    return enabled;
}

ContextState& state_for(EGLContext ctx, int height) {
    auto [it, inserted] = g_states.try_emplace(ctx);
    ContextState& st = it->second;
    if (inserted) {
        st.imgui = ImGui::CreateContext();
        ImGui::SetCurrentContext(st.imgui);
        ImGuiIO& io = ImGui::GetIO();
        io.IniFilename = nullptr;  // the game's own ImGui uses imgui.ini; don't collide
        io.ConfigFlags |= ImGuiConfigFlags_IsTouchScreen;
        ImGui::StyleColorsDark();
        // Phones run at 1080p+; scale against a 720p baseline.
        float scale = height > 720 ? height / 720.0f : 1.0f;
        ImGui::GetStyle().ScaleAllSizes(scale);
        io.FontGlobalScale = scale;
        ImGui_ImplOpenGL3_Init("#version 300 es");
        LOGI("overlay initialised for context %p on tid %d (scale %.2f)", ctx, gettid(), scale);
    }
    ImGui::SetCurrentContext(st.imgui);
    return st;
}

}  // namespace

void overlay_screen_size(int* width, int* height) {
    *width = g_screen_w;
    *height = g_screen_h;
}

uint8_t* overlay_back_buffer(int width, int height) {
    if (width <= 0 || height <= 0 || width > 8192 || height > 8192) return nullptr;
    g_back.resize(static_cast<size_t>(width) * height * 4);
    return g_back.data();
}

void overlay_publish(int width, int height) {
    std::lock_guard lock(g_frame_mutex);
    if (width > 0 && g_back.size() == static_cast<size_t>(width) * height * 4) {
        g_back.swap(g_front);
        g_front_w = width;
        g_front_h = height;
    } else {
        g_front_w = g_front_h = 0;
    }
    g_front_seq++;
}

void overlay_frame(EGLDisplay display, EGLSurface surface) {
    EGLContext ctx = eglGetCurrentContext();
    if (ctx == EGL_NO_CONTEXT) return;

    EGLint w = 0, h = 0;
    eglQuerySurface(display, surface, EGL_WIDTH, &w);
    eglQuerySurface(display, surface, EGL_HEIGHT, &h);
    if (w <= 0 || h <= 0) return;

    g_screen_w = w;
    g_screen_h = h;

    std::lock_guard lock(g_mutex);
    ContextState& st = state_for(ctx, h);

    auto now = std::chrono::steady_clock::now();
    ImGuiIO& io = ImGui::GetIO();
    io.DisplaySize = ImVec2((float)w, (float)h);
    io.DeltaTime = std::max(1e-4f, std::chrono::duration<float>(now - st.last_frame).count());
    st.last_frame = now;
    st.frames++;

    GLint prev_fbo = 0;
    glGetIntegerv(GL_DRAW_FRAMEBUFFER_BINDING, &prev_fbo);
    glBindFramebuffer(GL_DRAW_FRAMEBUFFER, 0);

    ImGui_ImplOpenGL3_NewFrame();
    ImGui::NewFrame();
    if (update_plugin_texture(st)) {
        ImDrawList* bg = ImGui::GetBackgroundDrawList();
        bg->AddCallback(premultiplied_blend, nullptr);
        bg->AddImage(static_cast<ImTextureID>(st.plugin_tex), ImVec2(0, 0), ImVec2((float)w, (float)h));
        bg->AddCallback(ImDrawCallback_ResetRenderState, nullptr);
    }
    if (!debug_window_enabled()) goto render;
    ImGui::SetNextWindowPos(ImVec2(16, 16), ImGuiCond_Always);
    ImGui::SetNextWindowBgAlpha(0.6f);
    ImGui::Begin("osrsrl", nullptr,
                 ImGuiWindowFlags_NoDecoration | ImGuiWindowFlags_AlwaysAutoResize | ImGuiWindowFlags_NoInputs);
    ImGui::Text("RuneLite host: hooked (build %s)", game::build_name());
    ImGui::Text("%dx%d  %.0f fps  frame %llu  tid %d", w, h, io.Framerate, (unsigned long long)st.frames, gettid());
    if (game::available()) {
        ImGui::Text("gameState %d  cycle %d  server tick %d", game::game_state(), game::cycle(), game::server_tick());
        if (game::game_state() == 30) {
            game::Point w = game::player_world(), l = game::player_local_tile(), f = game::player_fine();
            ImGui::Text("player world %d,%d,%d  scene %d,%d  local %d,%d", w.x, w.y, game::player_plane(), l.x, l.y, f.x, f.y);
            for (const game::Npc& n : client_thread::npcs())
                ImGui::Text("npc #%d %s (%d) at %d,%d", n.index, n.name.c_str(), n.id, n.world_x, n.world_y);
        }
        ImGui::Text("client thread: %s, %llu cycles", client_thread::installed() ? "hooked" : "not hooked",
                    (unsigned long long)client_thread::cycles());
        ImGui::Text("plugin host: %s", jni_bridge_status());
        for (const std::string& line : client_thread::recent_events()) ImGui::TextUnformatted(line.c_str());
    }
    ImGui::End();
render:
    ImGui::Render();
    ImGui_ImplOpenGL3_RenderDrawData(ImGui::GetDrawData());

    glBindFramebuffer(GL_DRAW_FRAMEBUFFER, prev_fbo);
}
