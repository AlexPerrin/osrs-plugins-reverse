#pragma once
#include <cstdint>
#include <EGL/egl.h>

// Draws the overlay into `surface`; call on the render thread just before eglSwapBuffers.
void overlay_frame(EGLDisplay display, EGLSurface surface);

// Plugin overlay frames, produced by the Java host on the game thread.
void overlay_screen_size(int* width, int* height);
// Back buffer of width*height RGBA (premultiplied) pixels for the producer to fill.
uint8_t* overlay_back_buffer(int width, int height);
// Swaps the back buffer in as the displayed frame; width 0 hides plugin overlays.
void overlay_publish(int width, int height);
