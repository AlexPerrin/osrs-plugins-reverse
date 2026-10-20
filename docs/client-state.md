# Client state

Short current-state doc for the client track (docs/workflow.md "State docs"). Rewrite it; don't append.
History: docs/client-handoff.md (per-gen log up to gen 40). Last rewrite: 2026-10-19, client dev gen 46.

## What this is

- The official OSRS Android app (build 241.4) repacked by `tools/repack.sh` to load `libosrsrl.so` (native/), which hooks
  the game thread and embeds the Java plugin host (host/). The host runs unmodified RuneLite plugins on ART: core
  plugins from the bundled client jar, Plugin Hub plugins built from source (`host/plugins/hub/build.sh`).
- Tested on BlueStacks Pie64 (adb `127.0.0.1:5555`, app uid 10071) against OpenRune (game `10.0.2.2:43594`,
  sidecar `10.0.2.2:8080` serving jav_config, LOCAL_AUTH sign-in, digest and payments param 2).
- API coverage and each plugin's status: docs/plugin-host.md. Addresses: docs/offsets.md (ELF; Ghidra = ELF + 0x100000).
- Privacy patches and Network QA: docs/app-telemetry.md (read it before any build that touches networking).

## Installed on the device (end of gen 45)

- Build: client/herbiboar-after-teleport's head (gen 45; includes client/host-prompt-enter), built in the gen 45 worktree with
  `OPENRUNE=1 SERVER=10.0.2.2:8080 GAME_PORT=43594 LOCAL_AUTH=1 MODULUS=<openrune.sh modulus> tools/repack.sh` (7 `telemetry:`
  lines) and installed with `adb install-multiple -r` (Windows adb.exe, `wslpath -w` paths). Reinstall master's build after the
  merge gates if they fail.
- Plugins dir: the default 3 only (grounditems, opponentinfo, quest-helper). Tester LOGGED_IN at 3219,3222.
- **Expected drift: a Fish barrel in the tester's inventory slot 15** (spawned gen 44; OpenRune's barrel Destroy does nothing,
  filed as a server task). Leave it (orchestrator, gen 44); destroy it once that fix is live. The restore check's MANUAL line says so.
- Pre-existing plugin data under `files/.runelite/` (bossing-info, c-engineer-sounds, cache, pyramid-plunder-counter,
  resource-packs-repository) predates gen 40; leave it.

## Recent state

- **ConfigManager.getRSProfiles (client/host-config-rsprofiles, gen 46, device 2026-10-19)**: Time Tracking's farming completion
  check no longer throws NoSuchMethodError. The host's one RS profile (`rsprofile.default`) gets desktop's profile keys
  (displayName, type, accountHash) at each login (`Host.postGameState`, before RuneScapeProfileChanged), and `getRSProfiles()`
  scans them as desktop. Device: keys written (`displayName=tester`, `type=STANDARD`); ~90 s of checks, no error. Evidence:
  gen 46 scratchpad `g46/full-b3.txt` (full logcat). No network change.
- **No second LOGGED_IN after a released rebuild hold (client/rebuild-double-logged-in, gen 46, device 2026-10-19)**: `Host`
  drops a native 30 when the last posted state is already LOGGED_IN (the hold's release replay posted it), logging
  `GameStateChanged LOGGED_IN again: already posted (rebuild released)`. Never seen before or now (qa/client-hostfixes-gen45.md
  review note). Device: login, 6 teleports (Lumbridge <-> Fossil Island) and a `::drop` relog each post exactly one
  LOADING/WorldViewLoaded/LOGGED_IN (CONNECTION_LOST -> LOGGED_IN unchanged); the guard line didn't fire. Evidence: gen 46
  scratchpad `g46/rebuild-guard-logcat.txt`. offsets.md: Client vtable slot 6 `0x421a20` is the draw on doCycle's thread. No
  network change.
- **Ground items that leave the scene despawn (client/scene-item-despawn, gen 46, device 2026-10-19)**: `SceneObjects.refresh`
  posted no ItemDespawned for an item whose tile is outside the new base after a rebuild, so Ground Items counted a dropped
  item twice after a teleport round trip (qa/client-hostfixes-gen45.md obs. 1). Those items now despawn on a tile with their
  old world location and item layer (local location null). Device: Bronze dagger dropped at 3219,3222, two round trips
  Lumbridge -> Fossil Island (3686,3870) -> Lumbridge: label "Bronze dagger (GE: 101 gp) (HA: 6 gp)" with no count, one
  LOADING/WorldViewLoaded/LOGGED_IN per teleport, no exceptions. Evidence: gen 46 session scratchpad `g46/`
  (`despawn-after-roundtrip.png`, `despawn-after-roundtrip2.png`, `scene-item-despawn-logcat.txt`). No network change.
- **Rebuild LOADING waits for the new scene base (client/herbiboar-after-teleport, gen 45, stacked on client/host-prompt-enter;
  device 2026-10-19)**: OpenRune's teleports are 30 -> 25 -> 30 and the host posted LOADING at 25 with the old scene base, so
  getMapRegions (from the base) gave the old regions and Herbiboar's area check on LOADING failed. `Host` now holds a 25 seen
  while logged in until the base changes, then posts LOADING (scene objects forgotten first), WorldViewLoaded and LOGGED_IN in
  one cycle (`GameStateChanged LOADING (scene rebuild)` in logcat); same-base rebuilds get the pair after 50 cycles. Device:
  after `::tele 3686 3870 0` from Lumbridge, core Herbiboar draws the start and trail tiles and Herbi AFK its path line (scene and
  minimap). A `setGameState(LOADING)` reload (probe `reload 0 0`) shows no native 25 to the host at all, so it posts no LOADING
  (unchanged by this fix; desktop would). Evidence: `g45/herbiboar-after-tele.png`, `g45/herbiafk-trail-line-after-tele.png`,
  `g45/herbiboar-tele-logcat.txt`. No network change.
- **Soft keyboard Enter for KeyListeners (client/host-prompt-enter, gen 45, device 2026-10-19)**: the game submits a chatbox
  input or prompt from its hidden EditText's editor action (IME_ACTION_DONE -> `com.jagex.android.t.a` -> key 66 through JNI
  `ru.hx`/`ru.wv`), which never passes the Window.Callback; on BlueStacks a hardware Enter's down also becomes that action (only
  its up reached KeyRouter). New `host/.../PromptEnter.java` wraps the listener when the EditText takes focus: VK_ENTER for
  KeyListeners first (KeyRouter.editorEnter, client thread, in order with held keys), then the game's submit once. Device:
  Decimal Prices' Withdraw-X "1.5k" withdrew 1500 (deposited back); chat "Gen45 once" with the keyboard shown sent once.
  Evidence: gen 45 session scratchpad `g45/` (`decimal-withdraw-1500.png`, `chat-enter-once.png`, `prompt-enter-logcat.txt`).
  No network change (host Java only).
- **v0.1.0 release gate PASS at 09c05c9** (qa/client-0.1.0.md; notes draft qa/client-0.1.0-notes.md).
  `THIRD_PARTY_NOTICES.md` (repo root) gives the notices and licence texts for everything third-party in libosrsrl.so and
  its host.jar (Dear ImGui + stb + ProggyClean, the RuneLite client subset/API/http-api, the host's Maven jars from
  host/build.sh, the client jar's shaders and CC-licensed fonts) and says the rest of the APK is Jagex's app. Releases
  attach it (`tools/release-dev.sh`). When host/build.sh's jar list or `native/third_party` changes, update the file.
- **Hub plugins after the cutover (client/hub-after-cutover, gen 44, device 2026-10-18 against integration with fix/75 + fix/76;
  rows in plugin-host.md, evidence in the gen 44 session scratchpad `g44/`)**: runs: Clue Juggling Timers (InfoBox countdown,
  warning, removed on despawn), Ground Items' despawn timer (`groundItemTimers=TICKS`), Coal Bag ("5" after Fill, "0" after
  Empty), Cannon Highlighter (hit grid + Single/Double/Unhittable labels; 1 of 3 set-ups drew nothing), Did I Compost
  (needs-compost icon after Inspect). Server-blocked (reported to main, filed as server tasks): dropped items all despawn after
  200 ticks (OSRS: 300, clues 61 min); Fish Barrel's Check text/interface and Destroy; Clue Steps' "Check steps" text lacks the
  tier; no compost on OpenRune. Host: Decimal Prices (the "." works, the submitting Enter never reaches KeyListeners, gap 6);
  core Herbiboar draws nothing after a `::tele` into Fossil Island from elsewhere but works after a relog there (gap 7);
  Herbi AFK draws no path line even then (cause not isolated). No build, no network change.
- **Ghidra project rebuilt (client/ghidra-reanalyse, 2026-10-18)**: `re/ghidra` is now the re-analysed copy (auto-analysis,
  `tools/ghidra_scripts/FixSwitches.java` to complete jump tables auto-analysis left unresolved or partial, auto-analysis
  again). Functions 24957 → 24961, instructions 1954259 → 2057546, instructions outside any function 8944 → 36; all 217
  function entries offsets.md names are unchanged and 18 of its addresses that were undisassembled are now inside functions.
  The old project had no user-made symbols, comments, signatures or data types, so nothing was carried over; it is kept
  at `re/ghidra-old` until main deletes it. New `Callers.java` (reverse skill table); the objdump whole-binary check stays.

- **World entry thread safety (client/world-entry-thread-safety, gen 43)**: `Native.worldEntry` no longer walks the game's
  world list off the game thread. The game frees and rebuilds that list (new address each time) whenever the login-screen
  world switcher opens, on the game thread (parser `0x4320dc`: callers `0x412e78` and the login handler `0x41615c`, both only under doCycle, and
  the CS2 world list ops 6500-6531 `0x4c2544`; offsets.md "World list").
  `client_thread::world_entry` serves other threads a copy of the current world's entry made in after_cycle (re-read on a
  world id or list change, else every 25 cycles); on the client thread it reads live. Logcat `osrsrl: world list: N entries
  at 0x...` marks each reload (a new address every time). Device (2026-10-18, World Hopper + Current World + Quick Prayer
  Preview loaded): 50 open/close cycles of the switcher (53 reloads in one process), then a relog (force-stop, 27 more
  cycles while the server released the session, Play Now): no crash; the off-thread "current world 255 from the world
  list" line came from a WorldService thread; Current World overlay "World 255 / Test server" and World Hopper's "255 0
  Test server" row unchanged; Quick Prayer Preview's held-orb tooltip shows both icons. No network change. Evidence: the
  gen 43 session scratchpad (`stress-logcat.txt`, `relog-logcat.txt`, `current-world-overlay.png`, `worldhopper-list.png`,
  `qpp-hold.png`, `relog-overlay.png`).

- **Current World runs (merged in master, gen 42)**: the host replaces RuneLite's WorldService
  (`host/src/net/runelite/client/game/WorldService.java`, same api.runelite.net fetch and schedule as 1.13.1) and, when the
  fetched list lacks `client.getWorld()`, adds that world built from the game's own slr.ws world list (global `0xace6c0`,
  `game::world_entry`, offsets.md "World list"), falling back to Client id/flags/host. If the api fetch failed the result
  holds just the current world. No new requests. Device: no NPE; overlay "World 255 / Test server" with overlayActivity on.
- **Gen 41 re-check of the 9 gen 40 hub plugins** (rows in plugin-host.md, evidence in the gen 41 session scratchpad `g41/`):
  runs: Monster Stats (panel opens from "Stats Man"), Multicolor Highlights, Pickpocket Guardian, Unpotted Reminder,
  Ground Item Sounds (MediaPlayer clip on a high-value drop), Quick Prayer Preview (after the fix below). Decimal Prices:
  starts, conversion not confirmed. No Examine: no effect (gap 2). Current World: NPE (gap 3).
- **BufferedImage drawing without dispose() (gen 41, merged in master)**: `CanvasGraphics.forImage` used to copy drawing back
  into the image only on `dispose()`; plugins that never dispose (Quick Prayer Preview's `getGraphics().drawImage`) got blank
  images. Now the image keeps a live mutable Bitmap: each draw marks it dirty, pixel reads/draws of the image sync it back.
- **R1 network gate (40c9ed2, device-verified)**: `Host.loadJar` runs `NetScan.refusal` on the cache copy before the
  DexClassLoader. NET-RAW jars are refused with `W osrsrl-host: netscan: refused <jar>: ...`. Vetted:
  `hub-loot-lookup` (raw refs only in bundled jsoup; approved), `devtools` (adb-only local server).
  Test override: `setprop debug.osrsrl.netallow <jar-name>`. Off-device check: `host/plugins/hub/netscan-check.sh <jars>`.
- **Build-time netcheck**: `host/plugins/hub/netcheck.sh` (run by hub build.sh): NET-RAW stops the build unless `ALLOW_NET=1`
  (orchestrator's OK). NET-OKHTTP plugins need Network QA, and new outbound hosts need the orchestrator's OK.
- **Network QA (v0.1.0 gate, 22:22-22:27)**: only the expected hosts (`dns.google` is an expected system lookup). With all hub
  plugins loaded, one GitHub socket (185.199.111.133:443), most likely C Engineer: Completed's approved sound GETs
  (docs/app-telemetry.md). No GE digest fetch captured yet.

## Open gaps (client)

1. **Chat Enter stall: resolved, not a plugin bug.** It reproduces with no hub plugins loaded and no KeyListeners (keylog
   silent): once the soft keyboard has been opened in a session (chat keyboard button 90,215, or a Withdraw-X prompt),
   Enter only submits chat while the soft keyboard is shown (`dumpsys input_method | grep mInputShown`). Right after login,
   before the keyboard was ever shown, hidden-keyboard Enter works. Check the chat input with devtools
   `get_var varcstr 335` (non-empty after a send = stalled); recover by showing the keyboard and pressing Enter.
   Gen 45: in that state the hidden-keyboard Enter does reach KeyListeners and is re-dispatched to the Activity (KeyManager
   logs press/typed/release), but the game doesn't submit; the cause is on the game side of the dispatch (not investigated).
2. **Removing native menu entries**: plugins that call `setMenuEntries(shorter)` in MenuOpened (No Examine) can't remove
   entries from the open long-press menu; the host only reorders and rewrites them.
3. **Current World**: runs (gen 42 + the gen 43 thread-safety fix). Not checked: its world switcher title (69:2); the in-game
   world switcher and the logout button weren't found on mobile (the bottom-left popout's door is "Minimise Popout"; the
   settings tab's three sub-tabs are display/audio/controls). Relog = force-stop, wait ~50 s, Play Now.
4. **OBJ_ADD timers: done** (fix/76 live; host reads them). The durations are the server's (200 ticks for every player
   drop), filed as a server task.
5. Random Event Hider can't be staged on OpenRune; Line of Sight's stray lines at 3219,3222 are terrain, not a bug.
6. **Prompt Enter for KeyListeners: fixed (client/host-prompt-enter, gen 45).** The soft keyboard's Enter is an editor action
   on the game's hidden EditText, not a key event; `PromptEnter` wraps that EditText's listener (docs/plugin-host.md row "Soft keyboard
   Enter"). Decimal Prices converts "1.5k" to 1500.
7. **Herbiboar after a teleport: fixed (client/herbiboar-after-teleport, gen 45).** A rebuild's LOADING now waits for the new
   scene base, so getMapRegions answers the new regions; Herbi AFK draws its path line too.
8. Host API gaps found by the v0.1.0 gate (backlog): no `javax.sound.sampled` (Drop Sounds throws at start).
   `ConfigManager.getRSProfiles()`: fixed (client/host-config-rsprofiles, gen 46).

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
- Typing into a prompt: `adb shell input keyevent KEYCODE_1 ...` works (digits, `KEYCODE_PERIOD` for plugins); read the input
  with `get_var varcstr 359`. The bank's long-press menu gains a "Withdraw-<last X>" entry after a valid X and loses it after
  an invalid one, so menu rows shift: screenshot the menu before tapping (gen 44 withdrew all bank coins by tapping a shifted
  row; deposited back). Valuable drops (cannon parts) ask "Really put it down?" before dropping. Untradeables have Destroy
  (coal bag works, fish barrel doesn't). `::tele` within the same zone posts no LOADING.
- Ground Item Sounds writes 3.6 MB of wavs to `.runelite/plugin-data/ground-item-sounds` on first start.
- Builds: client builds are host/build.sh + `ninja -C re/build` + repack (no docker). Any build container goes through
  `tools/buildq.sh client<gen> -- docker run ...` (2 slots), not gradle.lock. Every APK goes through `tools/repack.sh`
  (7 `telemetry:` lines).
- Network QA only when a build can change network behaviour (list in app-telemetry.md); any new outbound host: stop and
  report at once.

## Next tasks

1. Merge gate for client/world-entry-thread-safety with client-qa. Regression on plugins that draw into images (Poison
   infobox, Instance Map, Bank Tags icons) with the merged BufferedImage fix; Quick Prayer Preview checked gen 43.
2. When the server tasks from gen 44 are live: Fish Barrel (Check), Clue Steps, Did I Compost's treated path, destroy the slot-15 Fish barrel.
3. More hub plugins from the ranking (`scratchpad/client38/ranking.json` or refetch); netcheck + stubcheck each.
4. Host gap 2 (native menu entry removal).
5. Opportunistic: a capture that includes a GE digest fetch (should go to the sidecar).
