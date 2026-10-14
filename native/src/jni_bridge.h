#pragma once
#include <jni.h>

// Starts the Java plugin host (host/) on a background thread: unpacks the embedded
// host.jar, loads it with DexClassLoader, registers net.runelite.host.Native and then
// forwards client_thread events to net.runelite.host.Host.onEvent on the game thread.
// Disabled with `setprop debug.osrsrl.host 0`.
void jni_bridge_start(JavaVM* vm);

// "starting", "ready", "failed" or "disabled", for the overlay.
const char* jni_bridge_status();
