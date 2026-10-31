# QA: client/world-entry-thread-safety
- Branch: `client/world-entry-thread-safety`
- Commit: `b4b11a480588bc662062b0e1b3c89b37c617b891`
## Verdict: **PASS**

Merge gate, client-qa, 2026-10-18 21:36-21:57. Change (client dev gen 43): `client_thread::cache_world` copies the
current world's world-list entry on the game thread in after_cycle (under `g_world_mutex`; re-read when the world id or
the list's {pointer, count} changes, else every 25 cycles); `client_thread::world_entry` reads live on the client thread
(game thread, or the EDT delegate while the game thread is parked) and otherwise returns the cached copy; JNI
`n_world_entry` uses it. Files: native/src/client_thread.{cpp,h}, game.{cpp,h} (`world_list_key`), jni_bridge.cpp,
host CurrentWorld.java (comment); docs offsets.md, plugin-host.md, client-state.md. The branch's 7571dbb and 735323c
(orchestrator docs, already on master) were not reviewed.

## Build (at the commit, clean detached worktree)
- `host/build.sh` rc 0 (host.jar 7990262 bytes, sha256 4c412c09...). The `awt shim missing: ...getSource()` lines are
  the build's usual report, not errors.
- Fresh `cmake -G Ninja` (NDK r27d, arm64-v8a, android-26, Release) + `ninja -C re/build`: rc 0, no warnings
  (libosrsrl.so sha256 059f9996...).
- `OPENRUNE=1 SERVER=10.0.2.2:8080 GAME_PORT=43594 LOCAL_AUTH=1 tools/repack.sh --install` (MODULUS passed explicitly
  from openrune.sh in the main tree, since the worktree has no `openrune/src/.data`): rc 0, **7 `telemetry:` lines**
  (Singular starter, consent sync POST, Firebase/Crashlytics manifest flags, 2 consent/sync URLs zeroed, group score host,
  runescape.com web domain); LOCAL_AUTH network security config and AppAuth patched; RSA modulus, jav_config, slr.ws,
  game port patched. Installed base/split sha256 (2cb4b928..., 0d8669e0...) equal re/out's.

## Device regression (Android emulator, OpenRune, LOCAL_AUTH "Tester", slot `qa`)
Plugins: default 3 + `hub-current-world`, `hub-quick-prayer-preview`, `worldhopper` (marker jar) and `devtools`, with
`worldhopper.ping/displayPing/menuOption=false` (nothing pings Jagex worlds) and `currentworld.overlayActivity=true`
written into settings.properties before the first start. All 7 started, no exception from Current World, WorldService
or World Hopper (only the known FlatDarkLaf and the WorldService first-run `TimeoutException`).

| Check | Result |
|---|---|
| Current World overlay | PASS: "World 255 / Test server" under the minimap after login, after the relog and after the `::drop` reconnect |
| World Hopper list | PASS: World Switcher panel lists `255 0 Test server` above the api worlds (301 Trade - Free, 302 Trade - Members, ...); no hop |
| Off-thread read uses the cache | PASS: `current world 255 from the world list ...` logged from tid 386 (a WorldService thread) while doCycle ran on tid 388; game thread logs `world list: 1 entries at 0x...` on each reload |
| Stress: login-screen world switcher | PASS: after a force-stop + launch, 60 open/Cancel cycles of the switcher with World Hopper and Current World running: 63 `world list:` reloads (6 distinct addresses, the allocator reuses them), same pid 324 throughout, no crash/FATAL/tombstone |
| Relog with World Hopper on | PASS: Play Now from that login screen -> LOGGED_IN in the same process; then `::drop` -> CONNECTION_LOST -> LOGGED_IN, same pid |
| Quick Prayer Preview | PASS: `varbit 4102 5` (probe), finger held on the prayer orb: the tooltip shows the Thick Skin and Clarity of Thought icons (long-press menu also opens, as documented); varbit set back to 0 |
| Default plugins sane | PASS: Opponent Information, Ground Items, Quest Helper started; attacks on Men hit (combat history: 7 and 4 damage, Men die in one hit, so the opponent box was not caught on screen); `::tele`, walking, tabs normal |

## Code review (threading)
- Locking: `g_world_id`, `g_world_list`, `g_world_read_at` are touched only in after_cycle (game thread). `g_world` and
  `g_world_ok` are written under `g_world_mutex` and read under it, copied out by value. No lock order issue (one mutex,
  no calls out while held). Correct.
- Lifetime: off-thread callers never dereference game memory now; even the client-fields fallback (Client `+0x3098`
  string) is read only on the game thread. The parser runs only on the game thread (below), so after_cycle's live read
  never overlaps it. The EDT delegate path reads live only while the game thread is parked at its safe point, also
  outside the parser. Correct.
- Staleness: off-thread data is at most 25 cycles old, or one cycle after a world id / list key change. If the list is
  rebuilt at the same address with the same count, the copy is refreshed within 25 cycles; nothing unsafe since only the
  copy is read. Acceptable for WorldService/World Hopper.
- Not blocking: before the first after_cycle (or if the id isn't the cached one) off-thread `worldEntry` returns null,
  so WorldService omits the current world for that call. On device the first cache (21:45:28.238) came before the host
  started (21:45:28.336). `CurrentWorld.logged` is a non-volatile static written from several threads (log
  de-duplication only).

## Native claims (reverse, read-only)
`re/osclient.so` (sha256 64abe9f4..., identical to the split's game lib), objdump over all of .text plus headless Ghidra
`-readOnly` (Xrefs, FindScalar, Decompile; Ghidra = ELF + 0x100000). ELF addresses:
1. Parser `0x4320dc` direct callers: exactly 5 sites, `0x4147d8` (in `0x412e78`), `0x416444`, `0x416c94`, `0x4193d8`
   (in `0x41615c`, eh_frame range `0x41615c..0x41aa80`), `0x4c2ad4` (in `0x4c2544`). No tail call, adrp/add, adr, data
   pointer or RELATIVE addend to it: no indirect call possible. **Confirmed.**
2. `0x41615c` (called at `0x4119e4`, `0x411a0c`, `0x411b2c`, `0x411b6c`) and `0x412e78` (`0x411b34`, `0x411c1c`) are
   called only from doCycle `0x411640` (referenced only from vtable slot 5, `0xa6a008`). **Confirmed.**
3. Client vptr `0xa69fe0`; slot 6 (`0xa6a010`) = `0x421a20`. `0x4c2544` is entered only by a tail call at `0x4e4c74` in
   the CS2 op dispatcher `0x4e06dc` (interpreter `0x4dd08c`, runScript `0x4dc2b8`, called from doCycle at `0x411e84`,
   from `0x421a20`, and from ~36 functions reached only through vtables). **Slot 6's thread is established**: the frame
   function `0x86bdc8` runs the tick loop (`0x400f10` -> `0x73bed8` -> slot 5 doCycle), then once `0x400f40` ->
   `0x73bfb8` -> slot 8 then slot 6 `0x421a20`, then presents (`0x86c308`); `0x73bed8`/`0x73bfb8` have one caller each.
   So doCycle and slot 6 run sequentially in one call on one thread and can't overlap. On device the separate swap tid
   (HUD tid 31675/402 vs doCycle 31669/388) is the GL render thread started by `pthread_create` at `0x9d6de0` (start
   `0x9f02c0` -> eglSwapBuffers), which has no static link to the frame function, the draw or runScript.
   **Not established statically**: that none of the ~36 vtable-only runScript entry points is ever called from another
   thread, and whether the render thread runs game callbacks from its command queue. offsets.md's "likely the draw; its
   thread not proven statically" can be upgraded to "the draw, on doCycle's thread (`0x86bdc8`)".
4. Publish order: the parser polls a request held at `0xaee7f8` (fetch spans cycles); once done, in a single call it
   sets the count, allocates and zeroes the new array, stores count `0xace6c8` and array `0xace6d0`, frees the old Worlds
   and array, fills each 0x68-byte World, sorts (`0x4782c8`), frees the request. An off-thread reader could see null,
   partial or freed entries; an after_cycle reader cannot. **Confirmed.**
5. Writers of `0xace6c8`/`0xace6d0`: the parser (`0x53226c`, `0x532288` Ghidra) and the static initialiser
   (`0x5abb28` Ghidra) only; GOT `0xa82518` is only read. **Confirmed.**

## Network spot-check (no network change claimed)
Socket sampler on app uid 10071 (ESTABLISHED, `/proc/net/tcp`+`tcp6`, 766 samples, 21:45:27-21:54:59): 10.0.2.2:43594,
10.0.2.2:8080; 104.26.9.233, 172.67.74.139 (runelite); 104.18.35.7, 172.64.152.249 (Braze); 172.64.155.231 (OneTrust);
172.217.114.4 (the expected Google 443 address), all :443. netd DNS (second process only; logcat was cleared at the
restart): `api.runelite.net`, `static.runelite.net`, `sdk.fra-01.braze.eu`. No Jagex/RuneScape host or address; all
on docs/app-telemetry.md's expected list. Full Network QA not required (no network-relevant change).

## Device restore
Test jars removed by name, `debug.osrsrl.devtools` 0 and `debug.osrsrl.set` cleared, settings.properties pushed back
from the pre-slot copy, restarted with the default 3 plugins, tester LOGGED_IN at 3219,3222,0 (inventory untouched);
`tools/device-restore-check.sh check /tmp/osrs-slot/qa`: all OK (position checked on the overlay). Lock freed 21:56:59.

Evidence: /tmp/claude-1000/-home-alex-osrs-plugins-reverse/a0087aa1-b8d3-439d-add9-f4cbee8fc5e6/scratchpad/qa43/
(current-world.png, s2s.png World Hopper, qppc.png, stress-logcat.txt, sock.txt, re/ static RE outputs) and
scratchpad/hostbuild.log, scratchpad/repack.log.
