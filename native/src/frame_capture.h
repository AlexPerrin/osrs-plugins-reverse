#pragma once
#include <EGL/egl.h>

#include <cstdint>
#include <vector>

// One-shot frame capture for the host's DrawManager (screenshots): request from any thread; the
// next eglSwapBuffers reads the finished frame (game + overlays) with glReadPixels.
void frame_capture_request();
// Called from the eglSwapBuffers hook (render thread) before the swap.
void frame_capture_frame(EGLDisplay display, EGLSurface surface);
// The captured frame as top-down ARGB pixels, once; false while none is ready.
bool frame_capture_take(std::vector<uint32_t>& argb, int& width, int& height);
