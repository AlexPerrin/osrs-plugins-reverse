#include <EGL/egl.h>
#include <GLES3/gl3.h>
#include <sys/system_properties.h>
#include <algorithm>
#include <chrono>
#include <cstdlib>
#include <atomic>
#include <mutex>
#include <unistd.h>
#include <cfloat>
#include <cstring>
#include <string>
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

// --- sidebar ---
struct SidebarNode {
    char kind;
    int depth;
    uint32_t color;  // ARGB, 0 = default
    std::string text;
};
std::mutex g_sb_mutex;  // guards the model, events and touch queue
std::string g_sb_model_text;
bool g_sb_model_dirty = false;
std::vector<int> g_sb_events;
struct Touch { int action; float x, y; };
std::vector<Touch> g_touches;
std::atomic<bool> g_sb_open{false};
std::atomic<bool> g_shift{false};  // virtual Shift key (toggle under the RL button)
// Screen rects (x0, y0, x1, y1) of the toggle button and the open window, from the last frame.
std::atomic<float> g_toggle_rect[4], g_window_rect[4];
bool g_touch_captured = false;  // UI thread only

// Plugin menu entries (createMenuEntry) shown as a strip under the game's long-press menu.
// Set from the game thread; UI-canvas units, scaled to the screen with the canvas height.
std::vector<std::string> g_pm_labels;
float g_pm_x = 0, g_pm_y = 0, g_pm_w = 0, g_pm_canvas_h = 0;
std::atomic<float> g_pm_rect[4];

// Sidebar icons: RGBA pixels by id (guarded by g_sb_mutex), and GL textures per EGL context
// (render thread only).
struct SidebarIcon { int w, h; std::vector<uint8_t> rgba; };
std::unordered_map<int, SidebarIcon> g_sb_icons;
std::unordered_map<EGLContext, std::unordered_map<int, GLuint>> g_sb_icon_tex;

// Texture for an icon id in the current context (0 if unknown). Render thread, g_sb_mutex not held.
GLuint sidebar_icon_texture(int id, int* w, int* h) {
    SidebarIcon icon;
    {
        std::lock_guard lock(g_sb_mutex);
        auto it = g_sb_icons.find(id);
        if (it == g_sb_icons.end()) return 0;
        *w = it->second.w;
        *h = it->second.h;
        auto& texs = g_sb_icon_tex[eglGetCurrentContext()];
        auto t = texs.find(id);
        if (t != texs.end()) return t->second;
        icon = it->second;
    }
    GLint prev = 0;
    glGetIntegerv(GL_TEXTURE_BINDING_2D, &prev);
    GLuint tex = 0;
    glGenTextures(1, &tex);
    glBindTexture(GL_TEXTURE_2D, tex);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
    glPixelStorei(GL_UNPACK_ALIGNMENT, 4);
    glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, icon.w, icon.h, 0, GL_RGBA, GL_UNSIGNED_BYTE, icon.rgba.data());
    glBindTexture(GL_TEXTURE_2D, prev);
    std::lock_guard lock(g_sb_mutex);
    g_sb_icon_tex[eglGetCurrentContext()][id] = tex;
    return tex;
}

// Render-thread copy of the model.
int g_sb_selected = 0;
std::vector<std::string> g_sb_tabs;
std::vector<SidebarNode> g_sb_nodes;

void sidebar_parse(const std::string& m) {
    g_sb_tabs.clear();
    g_sb_nodes.clear();
    size_t pos = 0;
    bool header = true;
    while (pos < m.size()) {
        size_t end = m.find('\n', pos);
        if (end == std::string::npos) end = m.size();
        std::string line = m.substr(pos, end - pos);
        pos = end + 1;
        std::vector<std::string> f;
        size_t a = 0;
        while (true) {
            size_t b = line.find('\t', a);
            f.push_back(line.substr(a, b == std::string::npos ? std::string::npos : b - a));
            if (b == std::string::npos) break;
            a = b + 1;
        }
        if (header) {
            header = false;
            if (f.size() >= 2 && f[0] == "S") {
                g_sb_selected = atoi(f[1].c_str());
                for (size_t i = 2; i < f.size(); i++) g_sb_tabs.push_back(f[i]);
            }
            continue;
        }
        if (f.size() < 4 || f[0].empty()) continue;
        g_sb_nodes.push_back({f[0][0], atoi(f[1].c_str()), static_cast<uint32_t>(strtoul(f[2].c_str(), nullptr, 16)), f[3]});
    }
}

bool in_rect(const std::atomic<float>* r, float x, float y) {
    return x >= r[0] && y >= r[1] && x < r[2] && y < r[3];
}

void store_rect(std::atomic<float>* r, ImVec2 p, ImVec2 s) {
    r[0] = p.x; r[1] = p.y; r[2] = p.x + s.x; r[3] = p.y + s.y;
}

ImVec4 argb(uint32_t c) {
    return ImVec4(((c >> 16) & 0xff) / 255.0f, ((c >> 8) & 0xff) / 255.0f, (c & 0xff) / 255.0f, 1.0f);
}

void sidebar_frame(int w, int h) {
    ImGuiIO& io = ImGui::GetIO();
    std::vector<int> events;
    {
        std::lock_guard lock(g_sb_mutex);
        if (g_sb_model_dirty) {
            sidebar_parse(g_sb_model_text);
            g_sb_model_dirty = false;
        }
    }
    bool tapped = io.MouseDragMaxDistanceSqr[0] < 24.0f * 24.0f;  // a tap, not a scroll drag
    float unit = h / 720.0f;
    bool open = g_sb_open;
    if (!open) {
        ImVec2 size(56 * unit, 40 * unit);
        ImGui::SetNextWindowPos(ImVec2(0, h * 0.42f), ImGuiCond_Always);
        ImGui::SetNextWindowBgAlpha(0.55f);
        ImGui::Begin("##rltoggle", nullptr, ImGuiWindowFlags_NoDecoration | ImGuiWindowFlags_AlwaysAutoResize |
                     ImGuiWindowFlags_NoSavedSettings | ImGuiWindowFlags_NoFocusOnAppearing);
        if (ImGui::Button("RL", size) && tapped) g_sb_open = true;
        // Virtual Shift for plugins that check isKeyPressed(KC_SHIFT) (Ground/Object Markers'
        // shift + long-press menu entries). A toggle: tap to hold, tap again to release.
        bool shift = g_shift;
        if (shift) ImGui::PushStyleColor(ImGuiCol_Button, ImVec4(0.85f, 0.55f, 0.1f, 1.0f));
        if (ImGui::Button("Shift", size) && tapped) g_shift = !shift;
        if (shift) ImGui::PopStyleColor();
        store_rect(g_toggle_rect, ImGui::GetWindowPos(), ImGui::GetWindowSize());
        ImGui::End();
        store_rect(g_window_rect, ImVec2(0, 0), ImVec2(0, 0));
        return;
    }
    store_rect(g_toggle_rect, ImVec2(0, 0), ImVec2(0, 0));
    float width = std::min(w * 0.34f, 420 * unit);
    ImGui::SetNextWindowPos(ImVec2(0, 0), ImGuiCond_Always);
    ImGui::SetNextWindowSize(ImVec2(width, (float)h), ImGuiCond_Always);
    ImGui::SetNextWindowBgAlpha(0.88f);
    ImGui::Begin("##rlsidebar", nullptr, ImGuiWindowFlags_NoDecoration | ImGuiWindowFlags_NoSavedSettings | ImGuiWindowFlags_NoMove);
    if (ImGui::Button("Close") && tapped) g_sb_open = false;
    for (size_t i = 0; i < g_sb_tabs.size(); i++) {
        ImGui::SameLine();
        bool sel = static_cast<int>(i) == g_sb_selected;
        if (sel) ImGui::PushStyleColor(ImGuiCol_Button, ImGui::GetStyleColorVec4(ImGuiCol_ButtonActive));
        std::string label = g_sb_tabs[i] + "##tab" + std::to_string(i);
        if (ImGui::Button(label.c_str()) && tapped) {
            events.push_back(1);
            events.push_back(static_cast<int>(i));
            g_sb_selected = static_cast<int>(i);
        }
        if (sel) ImGui::PopStyleColor();
    }
    if (g_sb_tabs.empty()) ImGui::TextUnformatted("No plugin panels");
    ImGui::Separator();
    ImGui::BeginChild("##content", ImVec2(0, 0), false, ImGuiWindowFlags_NoScrollbar);
    // Touch scrolling: drag anywhere in the content.
    if (ImGui::IsWindowHovered(ImGuiHoveredFlags_AllowWhenBlockedByActiveItem) && ImGui::IsMouseDragging(0, 8.0f))
        ImGui::SetScrollY(ImGui::GetScrollY() - io.MouseDelta.y);
    int prev_depth = -1;
    char prev_kind = 0;
    for (size_t i = 0; i < g_sb_nodes.size(); i++) {
        const SidebarNode& n = g_sb_nodes[i];
        std::string id = "##n" + std::to_string(i);
        bool same_row = ((n.kind == 'B' && n.text.size() <= 3) || n.kind == 'I') && (prev_kind == 'L' || prev_kind == 'B') && n.depth == prev_depth;
        if (same_row) ImGui::SameLine();
        bool clicked = false;
        switch (n.kind) {
            case 'L':
                if (n.color) ImGui::PushStyleColor(ImGuiCol_Text, argb(n.color));
                ImGui::TextWrapped("%s", n.text.c_str());
                if (n.color) ImGui::PopStyleColor();
                break;
            case 'H':
                clicked = ImGui::Selectable((n.text + id).c_str());
                break;
            case 'B':
                clicked = ImGui::Button((n.text + id).c_str());
                break;
            case 'C': {
                bool v = !n.text.empty() && n.text[0] == '1';
                clicked = ImGui::Checkbox((n.text.substr(1) + id).c_str(), &v);
                break;
            }
            case 'T':  // text components: QH uses JTextAreas as wrapping labels
                if (n.text.empty()) ImGui::TextDisabled("[ ]");
                else ImGui::TextWrapped("%s", n.text.c_str());
                break;
            case 'E': {  // text field: tap opens an Android text prompt (Sidebar.promptText)
                std::string label = (n.text.empty() ? std::string("Search...") : n.text) + id;
                ImGui::PushStyleColor(ImGuiCol_Button, ImGui::GetStyleColorVec4(ImGuiCol_FrameBg));
                clicked = ImGui::Button(label.c_str(), ImVec2(-1, 0));
                ImGui::PopStyleColor();
                break;
            }
            case 'A': {  // editable text area (Notes): tap opens a multi-line Android prompt
                ImGui::PushStyleColor(ImGuiCol_ChildBg, ImGui::GetStyleColorVec4(ImGuiCol_FrameBg));
                ImGui::BeginChild(("##area" + std::to_string(i)).c_str(), ImVec2(-1, 0),
                                  ImGuiChildFlags_Borders | ImGuiChildFlags_AutoResizeY, ImGuiWindowFlags_NoScrollbar);
                if (n.text.empty()) ImGui::TextDisabled("(tap to write)");
                else ImGui::TextWrapped("%s", n.text.c_str());
                ImGui::EndChild();
                ImGui::PopStyleColor();
                clicked = ImGui::IsItemHovered(ImGuiHoveredFlags_AllowWhenBlockedByActiveItem) && ImGui::IsMouseReleased(0);
                break;
            }
            case 'I': {  // icon-only tab/button: "<icon id>:<selected>"
                int iw = 0, ih = 0;
                GLuint tex = sidebar_icon_texture(atoi(n.text.c_str()), &iw, &ih);
                bool sel = n.text.size() >= 2 && n.text.back() == '1';
                float side = ImGui::GetFrameHeight() * 1.6f;
                if (same_row) side = ImGui::GetTextLineHeight();  // small icon button after a label (QH's ">")
                if (!same_row && prev_kind == 'I' && n.depth == prev_depth &&
                    ImGui::GetItemRectMax().x + ImGui::GetStyle().ItemSpacing.x + side + 2 * ImGui::GetStyle().FramePadding.x <
                        ImGui::GetWindowPos().x + ImGui::GetWindowContentRegionMax().x)
                    ImGui::SameLine();
                if (sel) ImGui::PushStyleColor(ImGuiCol_Button, ImGui::GetStyleColorVec4(ImGuiCol_ButtonActive));
                if (tex) {
                    float sc = side / std::max(iw, ih);
                    clicked = ImGui::ImageButton(id.c_str(), static_cast<ImTextureID>(tex), ImVec2(iw * sc, ih * sc));
                } else {
                    clicked = ImGui::Button(("?" + id).c_str(), ImVec2(side, side));
                }
                if (sel) ImGui::PopStyleColor();
                break;
            }
            case 'X': {  // combo box: tap opens an Android list (Sidebar.promptChoice)
                std::string label = n.text + "  v" + id;
                ImGui::PushStyleColor(ImGuiCol_Button, ImGui::GetStyleColorVec4(ImGuiCol_FrameBg));
                clicked = ImGui::Button(label.c_str(), ImVec2(-1, 0));
                ImGui::PopStyleColor();
                break;
            }
        }
        if (clicked && tapped) {
            events.push_back(2);
            events.push_back(static_cast<int>(i));
        }
        prev_depth = n.depth;
        prev_kind = n.kind;
    }
    ImGui::EndChild();
    store_rect(g_window_rect, ImGui::GetWindowPos(), ImGui::GetWindowSize());
    ImGui::End();
    if (!events.empty()) {
        std::lock_guard lock(g_sb_mutex);
        g_sb_events.insert(g_sb_events.end(), events.begin(), events.end());
    }
}

// The plugin entries strip; a tap queues event (3, index) for Sidebar.java.
void plugin_menu_frame(int h, bool tapped) {
    std::vector<std::string> labels;
    float x, y, w, ch;
    {
        std::lock_guard lock(g_sb_mutex);
        labels = g_pm_labels;
        x = g_pm_x; y = g_pm_y; w = g_pm_w; ch = g_pm_canvas_h;
    }
    if (labels.empty() || ch <= 0) {
        store_rect(g_pm_rect, ImVec2(0, 0), ImVec2(0, 0));
        return;
    }
    float s = h / ch;  // canvas units -> screen pixels
    ImGui::SetNextWindowPos(ImVec2(x * s, y * s), ImGuiCond_Always);
    ImGui::SetNextWindowSize(ImVec2(w * s, 0), ImGuiCond_Always);
    ImGui::SetNextWindowBgAlpha(0.92f);
    ImGui::PushStyleColor(ImGuiCol_WindowBg, ImVec4(0.29f, 0.25f, 0.19f, 1.0f));
    ImGui::PushStyleColor(ImGuiCol_Border, ImVec4(0.0f, 0.0f, 0.0f, 1.0f));
    ImGui::Begin("##rlmenu", nullptr, ImGuiWindowFlags_NoDecoration | ImGuiWindowFlags_NoSavedSettings |
                 ImGuiWindowFlags_NoMove | ImGuiWindowFlags_AlwaysAutoResize | ImGuiWindowFlags_NoFocusOnAppearing);
    std::vector<int> events;
    for (size_t i = 0; i < labels.size(); i++) {
        std::string id = labels[i] + "##pm" + std::to_string(i);
        ImGui::PushStyleColor(ImGuiCol_Text, ImVec4(0.0f, 1.0f, 1.0f, 1.0f));
        if (ImGui::Selectable(id.c_str(), false, 0, ImVec2(0, 25 * s * 0.8f)) && tapped) {
            events.push_back(3);
            events.push_back(static_cast<int>(i));
        }
        ImGui::PopStyleColor();
    }
    store_rect(g_pm_rect, ImGui::GetWindowPos(), ImGui::GetWindowSize());
    ImGui::End();
    ImGui::PopStyleColor(2);
    if (!events.empty()) {
        std::lock_guard lock(g_sb_mutex);
        g_sb_events.insert(g_sb_events.end(), events.begin(), events.end());
    }
}

void feed_touches() {
    std::vector<Touch> touches;
    {
        std::lock_guard lock(g_sb_mutex);
        touches.swap(g_touches);
    }
    ImGuiIO& io = ImGui::GetIO();
    for (const Touch& t : touches) {
        io.AddMouseSourceEvent(ImGuiMouseSource_TouchScreen);
        io.AddMousePosEvent(t.x, t.y);
        if (t.action == 0) io.AddMouseButtonEvent(0, true);
        if (t.action == 1 || t.action == 3) {
            io.AddMouseButtonEvent(0, false);
            io.AddMousePosEvent(-FLT_MAX, -FLT_MAX);
        }
    }
}

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

void sidebar_set_model(const char* model) {
    std::lock_guard lock(g_sb_mutex);
    g_sb_model_text = model ? model : "";
    g_sb_model_dirty = true;
}

std::vector<int> sidebar_poll_events() {
    std::lock_guard lock(g_sb_mutex);
    std::vector<int> out;
    out.swap(g_sb_events);
    return out;
}

bool sidebar_open() { return g_sb_open; }

void sidebar_set_icon(int id, int w, int h, const std::vector<uint32_t>& argb) {
    SidebarIcon icon{w, h, std::vector<uint8_t>(argb.size() * 4)};
    for (size_t i = 0; i < argb.size(); i++) {
        uint32_t c = argb[i];
        icon.rgba[i * 4 + 0] = (c >> 16) & 0xff;
        icon.rgba[i * 4 + 1] = (c >> 8) & 0xff;
        icon.rgba[i * 4 + 2] = c & 0xff;
        icon.rgba[i * 4 + 3] = (c >> 24) & 0xff;
    }
    std::lock_guard lock(g_sb_mutex);
    g_sb_icons[id] = std::move(icon);
    for (auto& [ctx, texs] : g_sb_icon_tex) texs.erase(id);  // ids are not reused, but stay safe (leaks a texture)
}
bool virtual_shift() { return g_shift; }

void plugin_menu_set(const std::vector<std::string>& labels, float x, float y, float w, float canvas_h) {
    std::lock_guard lock(g_sb_mutex);
    g_pm_labels = labels;
    g_pm_x = x; g_pm_y = y; g_pm_w = w; g_pm_canvas_h = canvas_h;
    if (labels.empty()) store_rect(g_pm_rect, ImVec2(0, 0), ImVec2(0, 0));
}

bool overlay_touch(int action, float x, float y) {
    // MotionEvent: 0 down, 1 up, 2 move, 3 cancel, 5/6 pointer down/up.
    if (action == 0) g_touch_captured = in_rect(g_toggle_rect, x, y) || in_rect(g_window_rect, x, y) || in_rect(g_pm_rect, x, y);
    if (!g_touch_captured) return false;
    if (action == 0 || action == 1 || action == 2 || action == 3) {
        std::lock_guard lock(g_sb_mutex);
        if (g_touches.size() < 512) g_touches.push_back({action, x, y});
    }
    if (action == 1 || action == 3) {
        g_touch_captured = false;
        return true;
    }
    return true;
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

    feed_touches();
    ImGui_ImplOpenGL3_NewFrame();
    ImGui::NewFrame();
    sidebar_frame(w, h);
    plugin_menu_frame(h, io.MouseDragMaxDistanceSqr[0] < 24.0f * 24.0f);
    if (update_plugin_texture(st)) {
        ImDrawList* bg = ImGui::GetBackgroundDrawList();
        bg->AddCallback(premultiplied_blend, nullptr);
        bg->AddImage(static_cast<ImTextureID>(st.plugin_tex), ImVec2(0, 0), ImVec2((float)w, (float)h));
        bg->AddCallback(ImDrawCallback_ResetRenderState, nullptr);
    }
    if (!debug_window_enabled() || g_sb_open) goto render;
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
