#pragma once
#include <cstdint>

// ScriptPreFired / ScriptPostFired: inline hooks on the game's CS2 VM (build 241.4).
//  - runScriptEvent(vm, ScriptEvent*, maxOps) 0x4df54c: a root script (RootPre before, Post after).
//  - script lookup by id 0x3f5658, when called from the GOSUB case (0x28, return address
//    0x4dd988): a proc is entered; its arguments are still on the int/object stacks (Pre).
//  - the locals swap 0x4dfd5c, when called from the RETURN case (0x15, return address 0x4de0e0):
//    a proc returned, its results are on the stacks (Post).
// Like RuneLite (ScriptVMMixin: INVOKE → ScriptPreFired, RETURN → ScriptPostFired).
namespace script_hooks {

enum Phase { Pre = 0, Post = 1, RootPre = 2 };
using Listener = void (*)(int phase, int script_id);

// Call from JNI_OnLoad, after game::init() and before the game runs any script.
bool install();
bool installed();

// The listener runs on the game thread, inside the VM, only while enabled.
void set_listener(Listener listener);
void set_enabled(bool enabled);

// The ScriptEvent of the innermost root script (valid inside a RootPre callback), or 0.
uintptr_t current_event();

struct Stats { uint64_t roots, gosubs, returns, delivered; };
Stats stats();

}  // namespace script_hooks
