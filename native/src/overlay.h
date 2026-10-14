#pragma once
#include <EGL/egl.h>

// Draws the overlay into `surface`; call on the render thread just before eglSwapBuffers.
void overlay_frame(EGLDisplay display, EGLSurface surface);
