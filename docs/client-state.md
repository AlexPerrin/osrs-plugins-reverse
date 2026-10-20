# Client state

Short current-state doc for the client track (docs/workflow.md "State"). Rewrite it; don't append.
History: docs/client-handoff.md (per-gen log; the last entry holds gens 41-46 and host-menu-entry-removal, moved here
from this file). Tasks and blockers: `tools/harness/task.py list --track client`. Last rewrite: 2026-10-20, client dev
cd50 (task hub-fish-barrel-check).

## What this is

- The official OSRS Android app (build 241.4) repacked by `tools/repack.sh` to load `libosrsrl.so` (native/), which hooks
  the game thread and embeds the Java plugin host (host/). The host runs unmodified RuneLite plugins on ART: core
  plugins from the bundled client jar, Plugin Hub plugins built from source (`host/plugins/hub/build.sh`).
- Tested on BlueStacks Pie64 (adb `127.0.0.1:5555`, app uid 10071) against OpenRune (game `10.0.2.2:43594`,
  sidecar `10.0.2.2:8080` serving jav_config, LOCAL_AUTH sign-in, digest and payments param 2).
- API coverage and each plugin's status: docs/plugin-host.md. Addresses: docs/offsets.md (ELF; Ghidra = ELF + 0x100000).
- Privacy patches and Network QA: docs/app-telemetry.md (read it before any build that touches networking).

## Releases and tooling

- **v0.1.0**: release gate PASS at 09c05c9 (qa/client-0.1.0.md; notes draft qa/client-0.1.0-notes.md). Everything since
  (gens 41-46, host-menu-entry-removal) is on master on the dev's check.
- `THIRD_PARTY_NOTICES.md` (repo root) covers everything third-party in libosrsrl.so and host.jar (Dear ImGui + stb +
  ProggyClean, the RuneLite client subset/API/http-api, host/build.sh's Maven jars, the client jar's shaders and
  CC-licensed fonts); the rest of the APK is Jagex's app. Releases attach it (`tools/release-dev.sh`). **Update it when
  host/build.sh's jar list or `native/third_party` changes.**
- Ghidra: `re/ghidra` is the re-analysed project (2026-10-18: auto-analysis, `tools/ghidra_scripts/FixSwitches.java` for
  unresolved jump tables, auto-analysis again; 24961 functions, 36 instructions outside functions; offsets.md's entries
  unchanged). No user symbols/comments in it. The old project stays at `re/ghidra-old` until main deletes it.
  `Callers.java` is in the reverse skill's table; the objdump whole-binary check stays.

## Installed on the device (last recorded: end of task hub-fish-barrel-check, 2026-10-20)

- Build: master a4f3740 (hub-batch-next merged; rebuilt by hub-fish-barrel-check, no code change), `OPENRUNE=1 LOCAL_AUTH=1
  MODULUS=<openrune.sh modulus> tools/repack.sh` (7 `telemetry:` lines), installed with `adb install-multiple -r`
  (Windows adb.exe, `\\wsl.localhost\...` paths). Building in a worktree: symlink each entry of the main checkout's `re/`
  except `build` and `out` (the shared `re/build` compiles the main checkout's native/, so configure your own:
  `cmake -S native -B re/build -G Ninja` with the NDK toolchain, arm64-v8a, android-26, Release), symlink `base.apk`
  and `split_config.arm64_v8a.apk`, and pass MODULUS explicitly (openrune/ isn't checked out there).
- Plugins dir: the default 3 only (grounditems, opponentinfo, quest-helper). Tester LOGGED_IN at 3219,3222.
- Tester inventory: 9 items (slots 0-8), slot 15 empty. The gen 44 Fish barrel (it held 3 raw shrimps) was destroyed
  through OpenRune's destroy confirmation (interface 584) in hub-fish-barrel-check.
- settings.properties: every login writes `rsprofile.rsprofile.default.{type,displayName,accountHash}`, which the slot
  backup of 2026-10-20 lacks, so the restore check reports OFF after a login; push the backup back while logged in to
  `files/settings.properties` (not `files/.runelite/`; the next login re-adds the 3 keys).
- Pre-existing plugin data under `files/.runelite/` (bossing-info, c-engineer-sounds, cache, pyramid-plunder-counter,
  resource-packs-repository) predates gen 40; leave it.

## Host behaviour worth knowing (details in docs/plugin-host.md rows)

- **Menus**: `PostMenuSort` (presort) and `MenuOpened` both run in a menu session (`AndroidMenu.beginSession`); afterwards
  `AndroidMenu.applyOrder` writes text edits back and sends a permutation or subset of the game's entries to
  `Native.menuReorder` (logcat `menu reorder (MenuOpened) [...] of N: true`). Entries with submenus can't be dropped
  (`game::menu_reorder`). Row "No Examine".
- **Game state on rebuilds**: OpenRune's teleports go 30 -> 25 -> 30. A 25 while logged in is held until the scene base
  changes, then LOADING (scene objects forgotten first), WorldViewLoaded and LOGGED_IN go out in one cycle (`GameStateChanged
  LOADING (scene rebuild)`); same-base rebuilds after 50 cycles. A native 30 after the replay already posted LOGGED_IN is
  dropped (`... again: already posted (rebuild released)`). A `setGameState(LOADING)` reload shows no native 25, so no
  LOADING is posted (desktop would). Row "Herbiboar".
- **Scene items**: items whose tile leaves the scene on a rebuild get `ItemDespawned` on a tile with their old world
  location and item layer. Ground item despawn/visible timers and ownership come from OBJ_ADD (fix/76 live).
- **Soft keyboard Enter** (`PromptEnter`): the IME's Done is an editor action on the game's hidden EditText, not a key
  event; with KeyListeners registered the host sends VK_ENTER through KeyManager first, then the game's submit once.
- **Camera targets**: `setCameraPitchTarget/YawTarget` write the game's targets (Client `+0x8949c/+0x894a0`); the game
  eases pitch/yaw towards them (Camera Smoothing runs). `debug.osrsrl.camlog 1` also logs every 30th target write.
- **Sprite overrides** of the sprite's canvas size are cropped to the game's trimmed block at its offset
  (`Native.spriteInfo`, Sprite +0x20..+0x2c); other sizes are still refused. Row "Poison".
- **Audio**: `javax.sound.sampled` shim (`host/awt/javax/sound/sampled`, HostClip plays through AudioPlayer/MediaPlayer).
  Drop Sounds' pin is dropped (its download URL 404s upstream).
- **ConfigManager.getRSProfiles()**: one profile `rsprofile.default` gets displayName/type/accountHash at each login
  (`Host.postGameState`, before RuneScapeProfileChanged).
- **Worlds**: the host's WorldService (same api.runelite.net fetch as 1.13.1) adds the current world from the game's
  slr.ws world list when the fetched list lacks it. The game rebuilds that list on the game thread whenever the
  login-screen world switcher opens, so off-thread `Native.worldEntry` returns a copy made in after_cycle
  (`client_thread::world_entry`; logcat `world list: N entries at 0x...` per reload). offsets.md "World list".
- **BufferedImage drawing**: `CanvasGraphics.forImage` draws into a live Bitmap shared with the image (no `dispose()`
  needed).
- **Network gates**: at load, `Host.loadJar` runs `NetScan.refusal` before the DexClassLoader and refuses NET-RAW jars
  (`W osrsrl-host: netscan: refused <jar>: ...`); vetted: `hub-loot-lookup` (raw refs only in bundled jsoup), `devtools`
  (adb-only local server); test override `setprop debug.osrsrl.netallow <jar-name>`; off-device
  `host/plugins/hub/netscan-check.sh <jars>`. At build, `host/plugins/hub/netcheck.sh` (run by hub build.sh) stops on
  NET-RAW unless `ALLOW_NET=1` (orchestrator's OK); NET-OKHTTP plugins need Network QA, new outbound hosts the
  orchestrator's OK.
- **Destroy confirmations** (interface 584): devtools `widget_action` can't hit its Yes on mobile (the hover lands on the
  scene); tap the Yes component's canvas centre scaled by 1920/894 (`dump_interface 584`, Yes = 584:1).
- **Network QA** (v0.1.0 gate, and gen 46 for Drop Sounds): only expected hosts (`dns.google` is an expected system
  lookup); the one GitHub socket is an approved raw.githubusercontent.com sound GET. No GE digest fetch captured yet
  (task netqa-ge-digest).

## Open gaps (client)

1. **Chat Enter stall (game side, not a plugin bug).** With no hub plugins and no KeyListeners: once the soft keyboard has
   been opened in a session (chat keyboard button 90,215, or a Withdraw-X prompt), Enter only submits chat while the
   soft keyboard is shown (`dumpsys input_method | grep mInputShown`). Before the keyboard was ever shown, hidden-keyboard
   Enter works. Check with devtools `get_var varcstr 335` (non-empty after a send = stalled); recover by showing the
   keyboard and pressing Enter. In that state the hidden-keyboard Enter reaches KeyListeners and is re-dispatched to the
   Activity, but the game doesn't submit (not investigated further).
2. **Current World**: its world switcher title (69:2) is unchecked: the in-game world switcher and the logout button
   weren't found on mobile (the bottom-left popout's door is "Minimise Popout"; the settings tab's sub-tabs are
   display/audio/controls). Relog = force-stop, wait ~50 s, Play Now.
3. Random Event Hider can't be staged on OpenRune; Line of Sight's stray lines at 3219,3222 are terrain, not a bug.
4. Herbi AFK, Cannon Highlighter: a gen 44 run each that drew nothing (Herbi AFK most likely the teleport gap, fixed;
   Cannon Highlighter 1 of 3 set-ups, not reproduced). Server-blocked hub plugins are tasks in the store.

## Gotchas

- Device: your task's `instance:` list is granted at dispatch; `tools/device-lock.sh take <your-name> "<what>"`, free when
  done (status shows OVERDUE past the granted time). The osrs-devtools MCP drives the Android device, only under the
  lock. It needs `devtools.jar` in plugins/ and `setprop debug.osrsrl.devtools 1` plus an app restart. If the MCP server
  failed to connect at session start, `python3 -I tools/devtools_mcp.py --call tools/call '{"name":...,"arguments":{}}'`
  reaches the same tools. adb.exe from WSL fails now and then (`UtilAcceptVsock accept4 failed 110`): retry in an
  `until` loop, or batch taps and `screencap -p /sdcard/...` in one `adb shell "..."` call.
- Slot start/end: `tools/device-restore-check.sh pre <dir>` before touching anything, `check <dir>` at the end (OK/OFF per
  item). Remove test jars and files by name, in a plain `adb shell rm` call of their own, never with wildcards. Hub plugins
  go on the device as the dexed `re/host/plugins-extra/hub-<name>.jar`, not `re/hub-build/<name>.jar` ("no
  plugin-classes.txt"). Back up with `adb exec-out cat` (`shell cat` adds CRLF). Don't use `tools/plugin-test.sh` for
  cleanup (it rm's `*.jar`).
- Login: screenshot first. Signed out: only "Sign in with Email / Username". After a force-stop the server often still
  holds the session: Play Now shows "Either your account is still logged in" -> wait ~30-60 s, "Try again" (958,668),
  Play Now (957,563). **Don't tap 958,668 blind**: on the Play Now screen it's "Sign out" (answer "No" at 1130,625).
- Chat commands: devtools `type_chat` (or `server/tools/device.sh cmd`) submits only while the soft keyboard is shown once
  it has been opened in the session (gap 1); verify with `get_var varcstr 335` or a screenshot. Item spawn is
  `::item <id> <n>` (no `::obj`), `::openbank`, `::mypos`. **`get_chat_history` under-reports repeated lines**; confirm
  by screenshot.
- Long-press = `input swipe x y x y 900`. Plugin MenuEntryAdded entries show in the plugin strip under the game menu,
  not in devtools' `get_npc_menu`.
- Probe hooks (`debug.osrsrl.set`, `probe`, `say`, `wdump`) run once per distinct value; `setprop` values with spaces
  or parentheses need `shell "setprop key 'value'"`. Probe output tag: `osrsrl-probe`. Plugin config:
  `debug.osrsrl.set 'config <group> <key>=<value>'` (`config <group> <key>` unsets it), not `probe`.
- Typing into a prompt: `adb shell input keyevent KEYCODE_1 ...` one key at a time (digits, `KEYCODE_PERIOD`); read the
  input with `get_var varcstr 359`. The bank's long-press menu gains a "Withdraw-<last X>" entry after a valid X and loses
  it after an invalid one, so rows shift: screenshot the menu before tapping (gen 44 withdrew all bank coins by tapping a
  shifted row). Valuable drops ask "Really put it down?". Untradeables have Destroy (coal bag and fish barrel work). `::tele` within the same zone posts no LOADING.
- Ground Item Sounds writes 3.6 MB of wavs to `.runelite/plugin-data/ground-item-sounds` on first start.
- Builds: host/build.sh + `ninja -C re/build` + repack (no docker). Any build container goes through
  `tools/buildq.sh` (not gradle.lock). Every APK goes through `tools/repack.sh` (7 `telemetry:` lines).
- Network QA only when a build can change network behaviour (list in app-telemetry.md); any new outbound host: stop and
  report at once.
