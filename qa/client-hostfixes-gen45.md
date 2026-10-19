# QA: client/herbiboar-after-teleport
- Branch: `client/herbiboar-after-teleport`
- Commit: `b9e4c8a36ead6c91b03b953500629aeb716f1fa1`
## Verdict: **PASS**

Merge gate, client-qa, 2026-10-19 00:18-00:52. One gate for two stacked branches: `client/herbiboar-after-teleport` at
b9e4c8a contains `client/host-prompt-enter` at 2b056a7. Changes: `PromptEnter` (the soft keyboard's Done editor action
runs VK_ENTER press/typed/release through KeyManager on the client thread, then the game's submit once unless consumed),
`KeyRouter` (drops an Enter key-up whose down the listeners never saw), `Sidebar` (installs it), `Host` (a game state 25
while logged in is held until the scene base changes, then LOADING, WorldViewLoaded, LOGGED_IN in one cycle with scene
objects forgotten first; 50-cycle fallback for a same-base rebuild); docs.

## Build (at the commit, clean detached worktree)
Main tree's ignored inputs symlinked (re/runelite, re/tools, re/hub*, re/devtools*, re/osclient.so, re/debug.keystore,
base.apk, split_config.arm64_v8a.apk); re/host, re/build, re/apk, re/out local to the worktree; MODULUS passed literally
(the value `openrune/scripts/openrune.sh modulus` prints).
- `host/build.sh` rc 0 (host.jar 7,992,480 bytes, sha256 `6d8abba4...`); cmake + `ninja -C re/build` rc 0 (libosrsrl.so
  sha256 `8a9a907f...`).
- `OPENRUNE=1 SERVER=10.0.2.2:8080 GAME_PORT=43594 LOCAL_AUTH=1 MODULUS=... tools/repack.sh`: rc 0, **7 `telemetry:` lines**
  (Singular starter, consent sync POST, Firebase/Crashlytics manifest flags, the 2 consent/sync URLs zeroed, group score host,
  runescape.com web domain); LOCAL_AUTH appauth smali, RSA modulus, jav_config, slr and game port patched.
- base.apk `e8840469...`, split.apk `cf6272d5...`; installed with `adb install-multiple -r`, installed copies match.

## Device (BlueStacks, OpenRune, LOCAL_AUTH "Tester", slot `qa`)
Extra jars: devtools, herbiboars, hub-herbi-afk, hub-decimal-prices, npchighlight (config Man,Imp,Rat), groundmarkers,
objectindicators. All 10 plugins started; `prompt enter: focus listener installed`; no host exceptions in logcat.

| Check | Result |
|---|---|
| Decimal Prices, Withdraw-X "1.5k" | PASS: keys 1, PERIOD, 5, K one at a time give varcstr 359 "1.5k"; Enter: one `key 10 (editor action) submitted`, bank coins 10000 -> 8500, inventory +1500 exactly (100289 -> 101789). Deposited back (Deposit-1500): bank 10000 |
| Withdraw-X plain "3" (no conversion) | PASS: one editor action, bank 10000 -> 9997 (not 9994); deposited back, bank 10000 |
| Chat with the keyboard shown sends once | PASS: "QA45 once" typed via the IME, Enter: one editor action, one MODCHAT in history, exactly one chatbox line widget with the text (interface 162 dump); `::mypos`, `::item`, `::tele` (x4) and `::drop` sent this way each ran once (one reply set / one "Teleported" each) |
| Chat Enter otherwise (gap 1) | Not worse: before the keyboard was ever shown in a process, devtools type_chat (hidden-keyboard Enter through KeyRouter) submits `::openbank`, `::tele`, `::item` once each. After the keyboard was shown and hidden, type_chat and a hardware Enter leave the text in varcstr 335 unsent, exactly master's documented gap 1; showing the keyboard and pressing Enter then sends it once |
| Herbiboar + Herbi AFK after `::tele 3686 3870 0` from Lumbridge | PASS: logcat `GameStateChanged LOADING (scene rebuild)` / `WorldViewLoaded base 3632,3816` / `LOGGED_IN` in the same ms, once; Herbiboar's start tiles and Herbi AFK's cyan path line (scene and minimap) drawn; Inspect on Rock 30519: "You find some fresh tracks.", trail tiles and the Herbi AFK line to the next spot. Master (same plugins, same teleport) draws nothing there, and after the teleport back leaves a stray Herbi AFK line in Lumbridge; this commit doesn't |
| Teleport back (`::tele 3221 3219 0`) and a second trip | PASS: one LOADING/WorldViewLoaded(3168,3168)/LOGGED_IN each time; 8134 objects respawned once; overlays drawn again on the second Fossil Island trip |
| Login | PASS: LOGGING_IN, LOADING, WorldViewLoaded, LOGGED_IN; no extra LOADING/LOGGED_IN pair |
| Relog: `::drop` (at Fossil Island and at Lumbridge) | PASS: CONNECTION_LOST then LOGGED_IN, no held rebuild, no extra events; Herbiboar tiles + Herbi AFK line still drawn; Ground Items label and NPC Indicators hulls drawn after it |
| Relog: force-stop + launch at Fossil Island | PASS: LOGIN_SCREEN -> LOGGING_IN -> LOADING -> WorldViewLoaded -> LOGGED_IN; Herbiboar trail tiles and minimap trail drawn. Herbi AFK draws no line until a varbit changes (it resets to IDLE on LOGGING_IN; the login path is unchanged by this commit, see observations) |
| Ground Items after teleport/relog | PASS (no regression): labels drawn ("Bronze dagger (GE: 101 gp) (HA: 6 gp)"); one spawn per item in the host log. After a round trip Lumbridge -> Fossil -> Lumbridge the label counts the dagger twice ("(2)"); **master does the same ("(3)") with the same timing**, so it is pre-existing (observation 1) |
| NPC Indicators after teleport/relog | PASS: hulls on Men/Imp after login, after the teleports and after `::drop`; no duplicates seen |
| Same-zone `::tele` | No native 25 at all (no LOADING posted, as documented), so the 50-cycle fallback was not reachable on the device; covered by the code review |

## Code review
- **PromptEnter / KeyRouter threading**: the editor action (main thread) only queues `KeyRouter.editorEnter`; listeners run
  on the client thread via `clientThread.invokeLater` (behind KeyRouter's held keys), then the game's listener is posted back
  to the main thread with `MAIN.post`, once, and skipped when the press or the typed '\n' was consumed (desktop semantics).
  The wrapper returns false for the deferred Done, as the game's listener does, so TextView still hides the IME. Other
  action ids, events with a KeyEvent, and the no-listener case go straight to the game's listener. With the default plugin
  set Ground Items' hotkey listener is always registered, so the deferred path is the normal chat path. The device runs
  above confirm it.
- `KeyRouter.enterDown` keeps listeners from seeing a release without a press. If an Enter down is routed and its up never
  comes (focus change), the next IME-originated up would be routed as an unpaired release. That's harmless for known listeners.
- **Host hold**: entered only for a 25 after a posted LOGGED_IN (login's LOADING follows LOGGING_IN and isn't held); a 30
  while held is swallowed; the tick releases it when the base changes (the replay in `postSceneReload` posts
  LOADING -> WorldViewLoaded -> LOGGED_IN) or after 50 cycles (LOADING + LOGGED_IN; no WorldViewLoaded for the same base,
  as before). LOGIN_SCREEN/CONNECTION_LOST/HOPPING while held reset it. `loggedInPosted` stays true during the hold, so
  `getGameState` reads the native state (LOADING while the game is at 25).
- Not guarded: if the game's 30 were dispatched *after* the tick that saw the new base (held state already released), it
  would reach `postGameState(LOGGED_IN)` a second time (WorldViewLoaded is deduplicated by base). Not seen on the device: every
  teleport logged exactly one LOGGED_IN. Worth a one-line guard later; not a merge blocker.

## Network (spot check; no network change claimed)
Socket sampler on the app uid 00:26-00:51 (both builds): 10.0.2.2:43594, api/static.runelite.net Cloudflare (104.26.8.233,
104.26.9.233, 172.67.74.139), Braze (104.18.35.7, 172.64.152.249), OneTrust (104.18.32.25, 172.64.155.231), Google 443
(172.217.116.4, 172.217.119.4). netd lookups: api.runelite.net, static.runelite.net, sdk.fra-01.braze.eu. No Jagex/RuneScape
host, no ConsentSync/Singular lines.

## Observations (not blocking)
1. **Pre-existing, host**: `SceneObjects.refresh` posts no ItemDespawned for ground items that leave the scene on a rebuild
   (the old item's tile is outside the new base, `tile == null`), and RuneLite 1.13.1's Ground Items clears only on
   ItemDespawned/WorldViewUnloaded. After returning to a scene, a still-present item spawns again and its label counts it
   twice. Master shows "(3)" (`master-grounditems-dup3.png`); this commit "(2)" (`grounditems-dup-b9e4c8a.png`). Fix
   candidate: post ItemDespawned for every old item (with a tile from the old base) before the new scene's spawns.
2. Herbi AFK after a full relog mid-trail draws no path line until a varbit changes (plugin resets on LOGGING_IN); the login
   path is untouched by this commit. Not compared on master on the device.
3. The 50-cycle fallback could not be exercised on OpenRune (no native 25 for a same-zone `::tele`).

## Slot
`tools/device-restore-check.sh check /tmp/osrs-slot/qa`: all OK (default 3 plugins, props cleared, settings.properties
identical after pushing the backup back, no new files, LOGGED_IN, no local-auth override). Manual: player at 3219,3222,
100,289 coins and the coin pouch, bank coins 10000, GE offer untouched, the expected Fish barrel in slot 15. The installed
build is this commit's (base `e8840469...`, split `cf6272d5...`). For the comparison master as of 00:42 (135260c; host and
native identical to the branch base b9e4c8a~2) was built the same way and installed temporarily (base `75fe2896...`), then replaced.
Device freed 00:51.

Evidence: /tmp/claude-1000/-home-alex-osrs-plugins-reverse/a0087aa1-b8d3-439d-add9-f4cbee8fc5e6/scratchpad/qa45/
(host-build.log, ninja.log, repack.log, sha.txt, master-*.log, logcat-login.txt, logcat-full1.txt, sockets.txt,
decimal-withdraw-8500.png, grounditems-before-tele.png, herbiboar-after-tele.png, herbiboar-trail-after-tele.png,
herbiboar-after-drop.png, herbiboar-after-relog.png, after-drop-reconnect.png, grounditems-dup-b9e4c8a.png,
master-herbiboar-after-tele-none.png, master-herbiafk-stray-line-lumbridge.png, master-grounditems-dup3.png).
