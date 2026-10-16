#include "frame_capture.h"

#include <GLES3/gl3.h>

#include <atomic>
#include <mutex>

#include "log.h"

namespace {
std::atomic<bool> g_requested{false};
std::mutex g_mutex;
std::vector<uint32_t> g_frame;
int g_width = 0, g_height = 0;
bool g_ready = false;
}  // namespace

void frame_capture_request() { g_requested.store(true); }

void frame_capture_frame(EGLDisplay display, EGLSurface surface) {
    if (!g_requested.exchange(false)) return;
    EGLint w = 0, h = 0;
    if (!eglQuerySurface(display, surface, EGL_WIDTH, &w) || !eglQuerySurface(display, surface, EGL_HEIGHT, &h) ||
        w <= 0 || h <= 0 || w > 8192 || h > 8192) {
        LOGE("frame capture: bad surface size %dx%d", w, h);
        return;
    }
    GLint read_fb = 0, pack = 4;
    glGetIntegerv(GL_READ_FRAMEBUFFER_BINDING, &read_fb);
    glGetIntegerv(GL_PACK_ALIGNMENT, &pack);
    glBindFramebuffer(GL_READ_FRAMEBUFFER, 0);
    glPixelStorei(GL_PACK_ALIGNMENT, 4);
    std::vector<uint8_t> rgba(static_cast<size_t>(w) * h * 4);
    glReadPixels(0, 0, w, h, GL_RGBA, GL_UNSIGNED_BYTE, rgba.data());
    glPixelStorei(GL_PACK_ALIGNMENT, pack);
    glBindFramebuffer(GL_READ_FRAMEBUFFER, read_fb);
    std::vector<uint32_t> argb(static_cast<size_t>(w) * h);
    for (int y = 0; y < h; y++) {  // GL rows are bottom-up
        const uint8_t* src = rgba.data() + static_cast<size_t>(h - 1 - y) * w * 4;
        uint32_t* dst = argb.data() + static_cast<size_t>(y) * w;
        for (int x = 0; x < w; x++, src += 4) dst[x] = 0xff000000u | (src[0] << 16) | (src[1] << 8) | src[2];
    }
    std::lock_guard lock(g_mutex);
    g_frame.swap(argb);
    g_width = w;
    g_height = h;
    g_ready = true;
}

bool frame_capture_take(std::vector<uint32_t>& argb, int& width, int& height) {
    std::lock_guard lock(g_mutex);
    if (!g_ready) return false;
    g_ready = false;
    argb.swap(g_frame);
    g_frame.clear();
    width = g_width;
    height = g_height;
    return true;
}
