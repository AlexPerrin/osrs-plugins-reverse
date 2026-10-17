#pragma once
#include <cstdint>
#include <EGL/egl.h>
#include <vector>

// Draws the overlay into `surface`; call on the render thread just before eglSwapBuffers.
void overlay_frame(EGLDisplay display, EGLSurface surface);

// Plugin overlay frames, produced by the Java host on the game thread.
void overlay_screen_size(int* width, int* height);
// Back buffer of width*height RGBA (premultiplied) pixels for the producer to fill.
uint8_t* overlay_back_buffer(int width, int height);
// Swaps the back buffer in as the displayed frame; width 0 hides plugin overlays.
void overlay_publish(int width, int height);

// Plugin sidebar (model format: host Sidebar.java). Model and events: game thread; touch: UI thread.
void sidebar_set_model(const char* model);
std::vector<int> sidebar_poll_events();
bool sidebar_open();
void sidebar_set_open(bool open);
// Pixels (ARGB) for sidebar icon nodes (kind I) with this id; uploaded as a texture on first draw.
void sidebar_set_icon(int id, int w, int h, const std::vector<uint32_t>& argb);
// The virtual Shift toggle under the RL button.
bool virtual_shift();
// Plugin-created menu entries drawn as a strip at (x, y), width w, in UI-canvas units (canvas_h
// = canvas height, for scaling); empty hides it. Taps come back as sidebar events (3, index).
#include <string>
void plugin_menu_set(const std::vector<std::string>& labels, float x, float y, float w, float canvas_h);
// Offers a touch (MotionEvent masked action, window pixels); true = consumed by the overlay.
bool overlay_touch(int action, float x, float y);
