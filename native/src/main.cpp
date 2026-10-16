// libosrsrl.so: loaded by the patched com.jagex.android.b.a() right after the game library.
#include <EGL/egl.h>
#include <jni.h>
#include <sys/system_properties.h>

#include "agent.h"
#include "client_thread.h"
#include "crash_log.h"
#include "frame_capture.h"
#include "game.h"
#include "gl_trace.h"
#include "got_hook.h"
#include "jni_bridge.h"
#include "log.h"
#include "net_trace.h"
#include "overlay.h"
#include "npc_probe.h"
#include "script_hooks.h"

namespace {

constexpr const char* kGameLib = "liblibs.hal.system.osclient.so";

using SwapFn = EGLBoolean (*)(EGLDisplay, EGLSurface);
SwapFn g_orig_swap = nullptr;

EGLBoolean hook_eglSwapBuffers(EGLDisplay display, EGLSurface surface) {
    overlay_frame(display, surface);
    frame_capture_frame(display, surface);
    gl_trace_frame();
    return g_orig_swap(display, surface);
}

}  // namespace

extern "C" JNIEXPORT jint JNI_OnLoad(JavaVM* vm, void*) {
    LOGI("libosrsrl loaded; game lib base %p", reinterpret_cast<void*>(module_base(kGameLib)));
    // `setprop debug.osrsrl.off 1`: install nothing (A/B tests against the stock game code).
    char off[PROP_VALUE_MAX] = {};
    if (__system_property_get("debug.osrsrl.off", off) > 0 && off[0] == '1') {
        LOGI("debug.osrsrl.off=1: no hooks, no host");
        return JNI_VERSION_1_6;
    }
    crash_log_install(kGameLib);
    g_orig_swap = reinterpret_cast<SwapFn>(got_hook(kGameLib, "eglSwapBuffers", reinterpret_cast<void*>(hook_eglSwapBuffers)));
    if (!g_orig_swap) {
        LOGE("eglSwapBuffers hook failed; overlay disabled");
        g_orig_swap = eglSwapBuffers;
    }
    net_trace_init(kGameLib);
    gl_trace_init(kGameLib);
    npc_probe_install(kGameLib);
    if (game::init() && client_thread::install()) script_hooks::install();
    agent_start();
    jni_bridge_start(vm);
    return JNI_VERSION_1_6;
}
