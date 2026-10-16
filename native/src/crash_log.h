#pragma once
// Logs the arm64 state of a fatal signal in the game process (pc/lr/fault address as offsets
// into the game library or libosrsrl, registers, a frame-pointer backtrace) before the default
// handler runs. Tombstones under BlueStacks only show libhoudini frames.
// Off unless debug.osrsrl.crashlog=1. Logs only faults whose pc is in the game library or ours,
// then always chains to the previous handler.
void crash_log_install(const char* game_lib);
