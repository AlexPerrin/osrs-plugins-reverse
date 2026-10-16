#pragma once

// Opt-in GL diagnostics (`setprop debug.osrsrl.gltrace 1`, read at startup): records the
// matrices the game uploads with glUniformMatrix4fv and logs them every few seconds. Used
// to find the camera projection.
void gl_trace_init(const char* module);
// Called once per presented frame from the swap hook.
void gl_trace_frame();
// Latest ViewTransforms UBO block (100 floats from the camera position; projection at [36],
// view-projection at [52]); false until one was captured (gltrace only, for now).
bool gl_view_transforms(float out[100]);
