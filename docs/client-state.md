# Client state

Short current-state doc for the client track (docs/workflow.md "State docs"). Rewrite it; don't append.
History: docs/client-handoff.md (per-gen log up to gen 40). Last rewrite: 2026-10-18, client dev gen 42.

## What this is

- The official OSRS Android app (build 241.4) repacked by `tools/repack.sh` to load `libosrsrl.so` (native/), which hooks
  the game thread and embeds the Java plugin host (host/). The host runs unmodified RuneLite plugins on ART: core
  plugins from the bundled client jar, Plugin Hub plugins built from source (`host/plugins/hub/build.sh`).
- Tested on BlueStacks Pie64 (adb `127.0.0.1:5555`, app uid 10071) against OpenRune (game `10.0.2.2:43594`,
  sidecar `10.0.2.2:8080` serving jav_config, LOCAL_AUTH sign-in, digest and payments param 2).
- API coverage and each plugin's status: docs/plugin-host.md. Addresses: docs/offsets.md (ELF; Ghidra = ELF + 0x100000).
- Privacy patches and Network QA: docs/app-telemetry.md (read it before any build that touches networking).

## Installed on the device (end of gen 42)

- Build: client/host-synthetic-world (master b9749a6 + the WorldService current-world entry), `OPENRUNE=1 SERVER=10.0.2.2:8080
  GAME_PORT=43594 LOCAL_AUTH=1 tools/repack.sh --install` (7 `telemetry:` lines; under WSL pass `ADB=.../adb.exe`, or the
  install silently doesn't happen). It doesn't include client/gen41-recheck's BufferedImage fix unless that was merged first.
- Plugins dir: the default 3 only (grounditems, opponentinfo, quest-helper). Tester LOGGED_IN at 3219,3222.
- Pre-existing plugin data under `files/.runelite/` (bossing-info, c-engineer-sounds, cache, pyramid-plunder-counter,
  resource-packs-repository) predates gen 40; leave it.

## Recent state

- **Current World runs (client/host-synthetic-world, gen 42)**: the host replaces RuneLite's WorldService
  (`host/src/net/runelite/client/game/WorldService.java`, same api.runelite.net fetch and schedule as 1.13.1) and, when the
  fetched list lacks `client.getWorld()`, adds that world built from the game's own slr.ws world list (global `0xace6c0`,
  `game::world_entry`, offsets.md "World list"), falling back to Client id/flags/host. If the api fetch failed the result
  holds just the current world. No new requests. Device: no NPE; overlay "World 255 / Test server" with overlayActivity on.
- **Gen 41 re-check of the 9 gen 40 hub plugins** (rows in plugin-host.md, evidence in the gen 41 session scratchpad `g41/`):
  runs: Monster Stats (panel opens from "Stats Man"), Multicolor Highlights, Pickpocket Guardian, Unpotted Reminder,
  Ground Item Sounds (MediaPlayer clip on a high-value drop), Quick Prayer Preview (after the fix below). Decimal Prices:
  starts, conversion not confirmed. No Examine: no effect (gap 2). Current World: NPE (gap 3).
- **BufferedImage drawing without dispose() (client/gen41-recheck)**: `CanvasGraphics.forImage` used to copy drawing back
  into the image only on `dispose()`; plugins that never dispose (Quick Prayer Preview's `getGraphics().drawImage`) got blank
  images. Now the image keeps a live mutable Bitmap: each draw marks it dirty, pixel reads/draws of the image sync it back.
- **R1 network gate (40c9ed2, device-verified)**: `Host.loadJar` runs `NetScan.refusal` on the cache copy before the
  DexClassLoader. NET-RAW jars are refused with `W osrsrl-host: netscan: refused <jar>: ...`. Vetted:
  `hub-loot-lookup` (raw refs only in bundled jsoup; approved), `devtools` (adb-only local server).
  Test override: `setprop debug.osrsrl.netallow <jar-name>`. Off-device check: `host/plugins/hub/netscan-check.sh <jars>`.
- **Build-time netcheck**: `host/plugins/hub/netcheck.sh` (run by hub build.sh): NET-RAW stops the build unless `ALLOW_NET=1`
  (orchestrator's OK). NET-OKHTTP plugins need Network QA, and new outbound hosts need the orchestrator's OK.
- **Network QA (gen 40)**: only the expected hosts (`dns.google` is an expected system lookup). No GE digest fetch captured yet.

## Open gaps (client)

1. **Chat Enter stall: resolved, not a plugin bug.** It reproduces with no hub plugins loaded and no KeyListeners (keylog
   silent): once the soft keyboard has been opened in a session (chat keyboard button 90,215, or a Withdraw-X prompt),
   Enter only submits chat while the soft keyboard is shown (`dumpsys input_method | grep mInputShown`). Right after login,
   before the keyboard was ever shown, hidden-keyboard Enter works. Check the chat input with devtools
   `get_var varcstr 335` (non-empty after a send = stalled); recover by showing the keyboard and pressing Enter.
2. **Removing native menu entries**: plugins that call `setMenuEntries(shorter)` in MenuOpened (No Examine) can't remove
   entries from the open long-press menu; the host only reorders and rewrites them.
3. **Current World**: fixed on client/host-synthetic-world (above). Not checked: its world switcher title (69:2); the in-game
   world switcher wasn't reached on mobile (the bottom-left popout's door is "Minimise Popout", not logout).
4. **OBJ_ADD timers** (server side, reported): ground item despawn timers read 0, so Clue Juggling Timers and Ground
   Items' despawn timer can't be verified until the server fix is deployed.
5. Random Event Hider can't be staged on OpenRune; Line of Sight's stray lines at 3219,3222 are terrain, not a bug.
6. Decimal Prices' conversion is unconfirmed: devtools can't type `.` into the mobile quantity prompt.

## Gotchas

- Device: ask the orchestrator for a slot, `tools/device-lock.sh take client "<what>"`, free when done (status shows
  OVERDUE past the granted time). The osrs-devtools MCP drives the Android device; use it only under the lock. It needs
  `devtools.jar` in plugins/ and `setprop debug.osrsrl.devtools 1` plus an app restart.
- Slot start/end: `tools/device-restore-check.sh pre <dir>` before touching anything, `check <dir>` at the end (prints
  OK/OFF per item; verified on the device gen 40: it flagged every leftover jar, prop and wav, then all OK after cleanup). Remove test jars and files by name, in a plain `adb shell rm` call of their own, never with wildcards. Hub plugins go
  on the device as the dexed `re/host/plugins-extra/hub-<name>.jar`, not `re/hub-build/<name>.jar` ("no plugin-classes.txt").
  Back up with `adb exec-out cat` (`shell cat` adds CRLF). Don't use `tools/plugin-test.sh` for cleanup (it rm's `*.jar`).
- Login: screenshot first. Signed out: only "Sign in with Email / Username". After a force-stop the server often still
  holds the session: Play Now shows "Either your account is still logged in" -> wait ~30-60 s, "Try again" (958,668),
  Play Now (957,563). **Don't tap 958,668 blind**: on the Play Now screen it's "Sign out" (answer "No" at 1130,625).
- Chat commands: devtools `type_chat` (or `server/tools/device.sh cmd`) submits only while the soft keyboard is shown once
  it has been opened in the session (gap 1); verify with `get_var varcstr 335` or a screenshot. Item spawn is `::item <id> <n>`
  (into the inventory; there is no `::obj` on the current server), `::openbank`, `::mypos`. **`get_chat_history` under-reports repeated lines**; confirm by screenshot.
- Long-press = `input swipe x y x y 900`. Plugin MenuEntryAdded entries show in the plugin strip under the game menu,
  not in devtools' `get_npc_menu`.
- Probe hooks (`debug.osrsrl.set`, `probe`, `say`, `wdump`) run once per distinct value; `setprop` values with spaces
  or parentheses need `shell "setprop key 'value'"`. Probe output tag: `osrsrl-probe`. Plugin config is set with `debug.osrsrl.set 'config <group> <key>=<value>'`
  (`config <group> <key>` unsets it), not with `probe`.
- Ground Item Sounds writes 3.6 MB of wavs to `.runelite/plugin-data/ground-item-sounds` on first start.
- Builds: client builds are host/build.sh + `ninja -C re/build` + repack (no docker). Any build container goes through
  `tools/buildq.sh client<gen> -- docker run ...` (2 slots), not gradle.lock. Every APK goes through `tools/repack.sh`
  (7 `telemetry:` lines).
- Network QA only when a build can change network behaviour (list in app-telemetry.md); any new outbound host: stop and
  report at once.

## Next tasks

1. Merge gate for client/gen41-recheck (BufferedImage live Bitmap) with client-qa; regression on plugins that draw into
   images (Poison infobox, Instance Map, Bank Tags icons, Quick Prayer Preview).
2. When the OBJ_ADD timer fix is deployed: Clue Juggling Timers and Ground Items' despawn timer.
3. More hub plugins from the ranking (`scratchpad/client38/ranking.json` or refetch); netcheck + stubcheck each.
4. Host gap 2 (native menu entry removal). Merge gate for client/host-synthetic-world.
5. Opportunistic: a capture that includes a GE digest fetch (should go to the sidecar); Decimal Prices with a real `.` key.
