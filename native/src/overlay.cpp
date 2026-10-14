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
    std::chrono::steady_clock::time_point last_frame = std::chrono::steady_clock::now();
    uint64_t frames = 0;
};

std::mutex g_mutex;
std::unordered_map<EGLContext, ContextState> g_states;

// `setprop debug.osrsrl.overlay 0` disables drawing (checked about once a second).
bool overlay_enabled() {
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

void overlay_frame(EGLDisplay display, EGLSurface surface) {
    if (!overlay_enabled()) return;
    EGLContext ctx = eglGetCurrentContext();
    if (ctx == EGL_NO_CONTEXT) return;

    EGLint w = 0, h = 0;
    eglQuerySurface(display, surface, EGL_WIDTH, &w);
    eglQuerySurface(display, surface, EGL_HEIGHT, &h);
    if (w <= 0 || h <= 0) return;

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
            ImGui::Text("player world %d,%d  scene %d,%d  local %d,%d", w.x, w.y, l.x, l.y, f.x, f.y);
            for (const game::Npc& n : client_thread::npcs())
                ImGui::Text("npc #%d %s (%d) at %d,%d", n.index, n.name.c_str(), n.id, n.world_x, n.world_y);
        }
        ImGui::Text("client thread: %s, %llu cycles", client_thread::installed() ? "hooked" : "not hooked",
                    (unsigned long long)client_thread::cycles());
        ImGui::Text("plugin host: %s", jni_bridge_status());
        for (const std::string& line : client_thread::recent_events()) ImGui::TextUnformatted(line.c_str());
    }
    ImGui::End();
    ImGui::Render();
    ImGui_ImplOpenGL3_RenderDrawData(ImGui::GetDrawData());

    glBindFramebuffer(GL_DRAW_FRAMEBUFFER, prev_fbo);
}
