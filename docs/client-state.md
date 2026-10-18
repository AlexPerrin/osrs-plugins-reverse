# Client state

Short current-state doc for the client track (docs/workflow.md "State docs"). Rewrite it; don't append.
History: docs/client-handoff.md (per-gen log up to gen 40). Last rewrite: 2026-10-18, client dev gen 40.

## What this is

- The official OSRS Android app (build 241.4) repacked by `tools/repack.sh` to load `libosrsrl.so` (native/), which hooks
  the game thread and embeds the Java plugin host (host/). The host runs unmodified RuneLite plugins on ART: core
  plugins from the bundled client jar, Plugin Hub plugins built from source (`host/plugins/hub/build.sh`).
- Tested on BlueStacks Pie64 (adb `127.0.0.1:5555`, app uid 10071) against OpenRune (game `10.0.2.2:43594`,
  sidecar `10.0.2.2:8080` serving jav_config, LOCAL_AUTH sign-in, digest and payments param 2).
- API coverage and each plugin's status: docs/plugin-host.md. Addresses: docs/offsets.md (ELF; Ghidra = ELF + 0x100000).
- Privacy patches and Network QA: docs/app-telemetry.md (read it before any build that touches networking).

## Installed on the device (end of gen 40)

- Build: master at 40c9ed2+ (R1 gate), `OPENRUNE=1 SERVER=10.0.2.2:8080 GAME_PORT=43594 LOCAL_AUTH=1 tools/repack.sh --install`,
  host.jar rebuilt after the gen 40 hub batch (the headless swing shim now has `StyleConstants.setAlignment`).
- Plugins dir: the default 3 only (grounditems, opponentinfo, quest-helper). Tester LOGGED_IN at 3219,3222.
- Pre-existing plugin data under `files/.runelite/` (bossing-info, c-engineer-sounds, cache, pyramid-plunder-counter,
  resource-packs-repository) predates gen 40; leave it.

## Recent state

- **R1 network gate (40c9ed2, device-verified)**: `Host.loadJar` runs `NetScan.refusal` on the cache copy before the
  DexClassLoader. NET-RAW jars are refused with `W osrsrl-host: netscan: refused <jar>: ...`. Vetted:
  `hub-loot-lookup` (raw refs only in bundled jsoup; approved), `devtools` (adb-only local server).
  Test override: `setprop debug.osrsrl.netallow <jar-name>`. Off-device check: `host/plugins/hub/netscan-check.sh <jars>`.
- **Build-time netcheck**: `host/plugins/hub/netcheck.sh` (run by hub build.sh): NET-RAW stops the build unless `ALLOW_NET=1`
  (orchestrator's OK). NET-OKHTTP plugins need Network QA, and new outbound hosts need the orchestrator's OK
  (pet-info was dropped for raw.githubusercontent.com).
- **Network QA (gen 40, 17:20-17:31)**: only the expected hosts. `dns.google` is an expected system lookup (Pie's
  Private DNS validation, no uid, no app socket). No GE digest fetch has been captured yet.
- **NPCComposition.getStats** (dc9a9c6) verified: Man [1,1,1,7,1,1], Aberrant spectre [1,90,1,90,1,105].
- **Hub**: 9 pinned in gen 40 (monsterstats, current-world, no-examine, decimal-prices, quick-prayer-preview,
  multicolor-highlights, pickpocket-guardian, unpotted-reminder, ground-item-sounds); 4 dropped (dude-wheres-my-stuff,
  companion-pet, camera-smoothing, pet-info). See plugin-host.md for each row.

## Open gaps (client)

1. **Chat Enter stopped once (open item)**: in one run with all 9 gen 40 hub plugins loaded, Enter didn't submit (typed
   `::cmd` piled up in the input). A bisect blamed Decimal Prices, but it relied on devtools `get_chat_history`, which
   under-reports. A retest with Decimal Prices and keylog on submitted 4/4. Cause unknown; if it recurs, use
   `setprop debug.osrsrl.keylog 1` and verify by screenshot.
2. **Removing native menu entries**: plugins that call `setMenuEntries(shorter)` in MenuOpened (No Examine) can't remove
   entries from the open long-press menu; the host only reorders and rewrites them.
3. **Current World**: NPE because world 255 isn't in WorldService's live list (desktop would do the same); a host-side
   synthetic World for the current world would fix it.
4. **OBJ_ADD timers** (server side, reported): ground item despawn timers read 0, so Clue Juggling Timers and Ground
   Items' despawn timer can't be verified until the server fix is deployed.
5. Random Event Hider can't be staged on OpenRune; Line of Sight's stray lines at 3219,3222 are terrain, not a bug.

## Gotchas

- Device: ask the orchestrator for a slot, `tools/device-lock.sh take client "<what>"`, free when done (status shows
  OVERDUE past the granted time). The osrs-devtools MCP drives the Android device; use it only under the lock. It needs
  `devtools.jar` in plugins/ and `setprop debug.osrsrl.devtools 1` plus an app restart.
- Slot start/end: `tools/device-restore-check.sh pre <dir>` before touching anything, `check <dir>` at the end (prints
  OK/OFF per item; verified on the device gen 40: it flagged every leftover jar, prop and wav, then all OK after cleanup). Remove test jars and files by name, in a plain `adb shell rm` call of their own, never with wildcards.
  Back up with `adb exec-out cat` (`shell cat` adds CRLF). Don't use `tools/plugin-test.sh` for cleanup (it rm's `*.jar`).
- Login: screenshot first. Signed out: only "Sign in with Email / Username". After a force-stop the server often still
  holds the session: Play Now shows "Either your account is still logged in" -> wait ~30-60 s, "Try again" (958,668),
  Play Now (957,563). **Don't tap 958,668 blind**: on the Play Now screen it's "Sign out" (answer "No" at 1130,625).
- Chat commands: `server/tools/device.sh cmd` loses text when the soft keyboard is hidden (WSL vsock errors are common);
  devtools `type_chat` is more reliable. **`get_chat_history` under-reports repeated lines**; confirm by screenshot.
- Long-press = `input swipe x y x y 900`. Plugin MenuEntryAdded entries show in the plugin strip under the game menu,
  not in devtools' `get_npc_menu`.
- Probe hooks (`debug.osrsrl.set`, `probe`, `say`, `wdump`) run once per distinct value; `setprop` values with spaces
  or parentheses need `shell "setprop key 'value'"`. Probe output tag: `osrsrl-probe`.
- Ground Item Sounds writes 3.6 MB of wavs to `.runelite/plugin-data/ground-item-sounds` on first start.
- Builds: client builds are host/build.sh + `ninja -C re/build` + repack (no docker). Any build container goes through
  `tools/buildq.sh client<gen> -- docker run ...` (2 slots), not gradle.lock. Every APK goes through `tools/repack.sh`
  (7 `telemetry:` lines).
- Network QA only when a build can change network behaviour (list in app-telemetry.md); any new outbound host: stop and
  report at once.

## Next tasks

1. Re-check the 9 gen 40 plugins' behaviour with chat commands (watch for gap 1)
   (ground-item-sounds with a spawned `::obj`, unpotted-reminder while attacking, monsterstats' Stats panel).
2. When the OBJ_ADD timer fix is deployed: Clue Juggling Timers and Ground Items' despawn timer.
3. More hub plugins from the ranking (`scratchpad/client38/ranking.json` or refetch); netcheck + stubcheck each.
4. Host gaps 2 and 3 (native menu entry removal; a synthetic World for Current World).
5. Opportunistic: a capture that includes a GE digest fetch (should go to the sidecar).
