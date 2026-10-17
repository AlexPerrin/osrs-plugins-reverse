#pragma once

// Logs the friends-list state machine (FriendSystem +0x150) around the packets that change it,
// if debug.osrsrl.friendprobe=1 at startup.
void friends_probe_install(const char* module);
