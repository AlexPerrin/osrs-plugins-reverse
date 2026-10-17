# Multi-client: desktop RuneLite and the Android app on one server

Goal: run the real desktop RuneLite with the real Quest Helper on the same test server as our modded
Android app. The desktop client is then a reference to diff the Android plugin host against:
highlights, step order, events and widget text, with both clients in the same server state.

Status (2026-10-17):
- The server side works.
- A headless desktop-protocol login passes against OpenRune while the Android app is signed in (see
  "Headless check").
- **Agent-driven desktop client works.** RuneLite 1.13.2 runs through a headless RSProx in an
  isolated home (`C:\osrs-test`). Agents start it, log in and drive it from WSL through
  OpenRune-Developer-Tools' MCP server. Checked in game at Lumbridge on 2026-10-17: tools/list
  (47 tools), walk_to, player position, and the Android `tester` visible in list_players. See
  "Desktop A/B client (agent-driven)". This is the default way to run the desktop side.
- The manual GUI route ("Steps for the user") still works the same way, but it uses the user's own
  `%USERPROFILE%`. Prefer the isolated client.

## Architecture

```
Windows desktop                                   WSL2 / Docker Desktop (published on Windows localhost)
+---------------------------+                     +--------------------------------------------------+
| RuneLite 1.13.2 (rev 241) |  127.x.y.3 loopback | osrs-openrune               :43594  game + JS5    |
|  + Plugin Hub Quest Helper|-----> RSProx -------> (OpenRune-Server, our openrune.patch)               |
+---------------------------+  re-encrypts the    |   desktop session: toplevel 161/548, desktop      |
          ^  jav_config/worlds  login with our key |   zone/info encoders                              |
          |                                       |   Android session: toplevel 601, Android encoders |
          +---------------------------------------- osrs-openrune-http-desktop :8082  jav_config.ws, |
                                                  |   slr.ws, /rsa (world host 127.0.0.1)              |
BlueStacks (Android app, our repack)              | osrs-openrune-http           :8080  same, world   |
  10.0.2.2:8080 / 10.0.2.2:43594 ---------------->|   host 10.0.2.2, plus the LOCAL_AUTH sign-in      |
                                                  +--------------------------------------------------+
```

- **One game server, two client types.**
  - OpenRune reads the client type from the login header (`NetworkScript.startSession`).
  - It allocates the info protocols for that type and sets `Player.mobileClient` only for
    ENHANCED_ANDROID.
  - Every Android-specific path in `openrune/openrune.patch` is gated on `mobileClient`: zone updates
    (`PlayerZoneUpdateProcessor`), the mobile toplevel 601 (`GameframeScript`) and the chatmodal
    unclamp (`PlayerInterfaceExtensions`). A desktop session takes the upstream paths unchanged.
- **Two HTTP sidecars, same image (`osrs-rsps-test:241`).**
  - The Android app needs world host 10.0.2.2 (BlueStacks' alias for the host).
  - A desktop client on Windows needs 127.0.0.1.
  - The sidecar's world host is global, so the desktop one is a separate compose service
    (`http-desktop` in `openrune/docker-compose.yml`, port `OPENRUNE_DESKTOP_HTTP_PORT`, default 8082).
- **Why RSProx and not plain RuneLite.**
  - RuneLite has `--jav_config <url>`, so it can be pointed at our jav_config.
  - It has no option for the login RSA modulus: the injected client encrypts the login block with
    Jagex's key, and our server cannot decrypt it ("Invalid RSA check").
  - RSProx patches RuneLite to use RSProx's own key. It then decrypts the login, re-encrypts it with
    our modulus, and forwards it to the world host from our world list.
  - It also pins the RuneLite version to rev 241, through the bootstrap commit, and gives desktop
    packet transcripts for free. It is the simplest working route.
- **Accounts.**
  - OpenRune creates an account on its first username/password login
    (`AccountLoaderService`, `SearchOrCreateWithPassword`). No registration step is needed and no
    Jagex account is involved.
  - Desktop uses the local test account **`desktop` / `desktop`**. This is a local-only test
    credential for our dev server; never reuse it anywhere else.
  - The Android app's token sign-in maps to the local account `tester` (password `android-local`,
    `SPIKE_TOKEN_*` in `ConnectionHandler`).
  - Every account on this dev realm has staff level 2, so `::` commands work on both clients.
- **Revision.**
  - The server is rev 241.
  - RuneLite 1.13.2 (2026-10-14) is the first rev-241 RuneLite, and the current one on 2026-10-16
    (RSProx archive index `archive.rsprox.net/runelite/index.json`).
  - The target pins its bootstrap (static.runelite.net commit `e617bef1`), so it keeps working after
    Jagex moves to 242.

## Server side (already running)

```
cd openrune && docker compose --env-file switch.env up -d --no-deps http-desktop   # starts only the new sidecar
curl -s http://127.0.0.1:8082/jav_config.ws | grep -E 'codebase|param=17'           # 127.0.0.1, :8082/slr.ws
```

Starting `http-desktop` creates no other container and does not touch `osrs-openrune` or
`osrs-openrune-http`, so the Android session is not affected.

## Headless check

`tools/multiclient/desktop_login.py` is a scripted rev-241 desktop client.
- What it does:
  - JS5 CRCs;
  - INIT_GAME_CONNECTION;
  - the login block with RSA and XTEA, client type 1 and password auth;
  - the SHA-256 proof of work;
  - ISAAC-decoded game packets, with sizes parsed from RSProt's desktop `GameServerProt`.
- It only reads, so it changes nothing in the world, and it disconnects after `--seconds`.

```
python3 -I tools/multiclient/desktop_login.py            # desktop/desktop on 127.0.0.1:43594, /rsa from :8082
```

Result on 2026-10-16. The Android app was signed in as `tester` at the same time; the server log
shows its token logins at 15:08 and 15:10.

```
login OK: user 'desktop', player index 9, staff 2
game messages: ['Welcome to OpenRune.', 'Did you know? ...']
packets: VARP_SMALLx76, VARP_LARGEx73, UPDATE_ZONE_FULL_FOLLOWSx49, ..., PLAYER_INFOx11,
         NPC_INFO_SMALL_V6x11, UPDATE_ZONE_PARTIAL_ENCLOSEDx4, REBUILD_NORMAL_V2x1, IF_OPENTOPx1, ...
  ok   IF_OPENTOP is a desktop toplevel 161 (toplevel_osrs_stretch)
  ok   REBUILD_NORMAL received
  ok   PLAYER_INFO stream 11 packets
RESULT: PASS
```

The whole stream decodes in sync with the desktop prot table, including the
UPDATE_ZONE_PARTIAL_ENCLOSED packets (desktop zone path). One packet with Android encoding would
have broken the decode.

Protocol notes:
- The login OK length byte is 37 for a 34-byte block. It also covers the 3-byte header of the
  first game packet (REBUILD_NORMAL_V2), which the client reads in the same read.
- If the previous run's session is still in the world, the login answers 5 ("already online"). The
  script retries for up to 75 s.

`tools/multiclient/proxy-targets.yaml` also loads with RSProx's own parser
(`YamlProxyTargetConfig.load`, run against the `re/rsprox` build): name, URL, modulus, revision
241.1, port 43594 and the 1.13.2 bootstrap pin all come through.

Not checked headlessly: that each client sees the other player. Seeing it means decoding
PLAYER_INFO bit by bit; it is step 6 of the GUI walkthrough.

## Desktop A/B client (agent-driven)

```
WSL                                      Windows (C:\osrs-test, user.home = C:\osrs-test\home)
tools/multiclient/desktop.sh ──powershell──> jdk\bin\java OsrsTestLauncher (headless RSProx)
                                               ├─ :43600 jav_config/world list (rewritten from :8082)
                                               ├─ :43701 game proxy ── re-encrypts login ──> OpenRune :43594
                                               └─ RuneLite launcher ─> RuneLite 1.13.2 client
                                                     └─ OpenRune-DeveloperTools, MCP on 127.0.0.1:7780
tools/multiclient/desktop-devtools-mcp.sh ──curl.exe (WSL interop)──> 127.0.0.1:7780/mcp
```

### Layout and isolation

| Path | What |
|---|---|
| `C:\osrs-test\jdk` | Temurin 11 **JDK** (Adoptium zip, sha256 checked). A JRE does not work: RSProx's RuneLite launcher signs its patched jars with `jdk.jartool` |
| `C:\osrs-test\rsprox\lib`, `\launcher` | RSProx runtime classpath (`re/rsprox`, 1.0.5) and `OsrsTestLauncher` |
| `C:\osrs-test\home\.rsprox` | RSProx home: `proxy-targets.yaml`, its RSA key, `binary\` transcripts, the RuneLite launcher it downloads |
| `C:\osrs-test\home\.runelite` | RuneLite **launcher** dir: `repository2` (patched 1.13.2 jars), `logs` |
| `C:\osrs-test\home\.rlcustom` | RuneLite **client** dir (RSProx renames `.runelite`): `profiles2`, `sideloaded-plugins`, `jagexcache`, `random.dat`, `jagex_cl_*.dat` |
| `C:\osrs-test\logs\rsprox-<ts>.out/.err` | RSProx, launcher and client stdout (one file per start) |

- Isolation is `JAVA_TOOL_OPTIONS=-Duser.home=C:\osrs-test\home`. It is set only in the PowerShell
  process that starts RSProx, never user- or machine-wide. RSProx, the RuneLite launcher and the
  client inherit it: each logs `Picked up JAVA_TOOL_OPTIONS`, and the client logs it under "Java VM
  arguments".
- Every path RSProx and RuneLite use is `user.home`-relative. This includes the Jagex-account
  credentials lookup (`.runelite/credentials.properties`), which only applies to the official
  target anyway.
- The user's `C:\Users\Alex\.runelite` and the installed RuneLite are never touched.
- `OsrsTestLauncher` (source `tools/multiclient/headless/`) refuses to start in any of these cases:
  - `user.home` is not under `C:\osrs-test`;
  - any `RSPS_*` or `JX_*` (Jagex launcher) variable is set;
  - the selected target is not exactly one "OpenRune Test" with a `http://127.0.0.1:` jav_config and
    its own modulus.

  It never builds the official target, and it exits when the client exits.
- What the game connection looks like (`desktop.sh status`, by our PIDs):
  - client → `127.0.255.3:43701` (RSProx);
  - RSProx → `127.0.0.1:43594` (OpenRune);
  - the client's jav_config/world list → RSProx `:43600`.
- Other outbound traffic, all non-Jagex:
  - RSProx: cdn.rsprox.net (its RuneLite launcher), archive.rsprox.net (clientscript names, by cache
    hash; answers 403 for our cache);
  - the launcher: raw.githubusercontent.com (pinned 1.13.2 bootstrap);
  - the client: *.runelite.net (artifacts, Plugin Hub, item prices).

### Plugins turned off in the isolated profile

There is no NetGuard on desktop. `desktop-build.sh` seeds `.rlcustom/settings.properties`, which
RuneLite imports into its first profile:
- `runelite.<x>plugin=false` for: discord, loottracker, xptracker (XpClient), xpupdater,
  crowdsourcing, grandexchange (trade submissions), chatcommands (kc/pb submissions), hiscore
  (Jagex hiscore web lookups), worldhopper and defaultworld (either could move the client to a real
  Jagex world), twitch, and party (`partyplugin2`);
- `runelite.openrunedevtoolsplugin=true`, `openrunedevtools.autoUpdate=false` (it is a `-local`
  build, so the GitHub update check is off anyway).

Telemetry is off (`--disable-telemetry`), and there is no RuneLite account login.

RuneLite's core DiscordService still connects to the local Discord app over IPC ("Discord RPC
service is ready"). It sets no presence while the Discord plugin is off.

Turn one of these plugins back on for an A/B only after checking that its submissions can't go
out (and with the orchestrator's OK).

### Build, start, log in, stop

```
tools/multiclient/make_proxy_target.sh     # refresh the modulus from :8082/rsa
tools/multiclient/desktop-build.sh         # Docker build of RSProx + launcher, stage into C:\osrs-test
                                           # (SKIP_RSPROX_BUILD=1 to only restage)
tools/multiclient/desktop.sh start         # checks 7780/43600-9/43700-19 are free; ~10 s to the login screen
tools/multiclient/desktop.sh login         # desktop/desktop via the Existing User form, retries ~2 min
tools/multiclient/desktop.sh status        # our processes (exe under C:\osrs-test) + their sockets
tools/multiclient/desktop.sh log 80        # tail of the newest log
tools/multiclient/desktop.sh logout        # in-game logout
tools/multiclient/desktop.sh stop          # logout, then kill only processes whose exe is under C:\osrs-test
```

- **First start only:** the client shows the terms screen. Click Accept at canvas (302,311) with
  `desktop-devtools-mcp.sh --tool click '{"x":302,"y":311}'`. Never click Decline or "New User":
  both open a browser.
- **Login clicks:** `login` uses fixed canvas coordinates (765x503, fixed mode).
  - After the first "Existing User" of each run, the client shows a "Upgrade to a Jagex Account"
    page. `login` clicks its **Login** button (never "Download Launcher") once per run.
  - After a failure the form stays open; `relogin` refills it.
- **Refused logins:** OpenRune refuses the account for a while after a session ends.
  - After a logout: CONNECT_FAIL 50, roughly 30 to 90 s.
  - After a killed client: "already logged in" (5), up to about 6 min.
  - `login` retries for about 2 minutes. `stop` logs out first to keep this short.
- **Server restarts:** the client shows "Unexpected server response". Click Try again at (382,311),
  then run `relogin`.
- **Windows firewall:** RSProx listens on all interfaces, because it serves a 127.x.y.z address per
  world and can't bind only 127.0.0.1. The first start showed a Windows Firewall prompt for
  `C:\osrs-test\jdk\bin\java.exe`.
  - On 2026-10-17 it was answered **Allow (Public)**. That exposes :43600/:43701 to the LAN.
  - Loopback is never filtered, so set the rules to Block (admin PowerShell):
    `Get-NetFirewallApplicationFilter | ? Program -like 'C:\osrs-test\*' | Get-NetFirewallRule | Set-NetFirewallRule -Action Block`
  - Answer any later prompt with **Cancel**.

### Driving it (MCP)

`tools/multiclient/desktop-devtools-mcp.sh`:
- An MCP stdio server, the same as `tools/devtools-mcp.sh` for Android. Register it as
  `{"command": "<repo>/tools/multiclient/desktop-devtools-mcp.sh"}`, for example as
  `osrs-devtools-desktop`.
- WSL runs in NAT mode, so Windows' 127.0.0.1 is not reachable from WSL. Each JSON-RPC line goes
  through Windows' `curl.exe` instead (about 0.1 s per call). Nothing is opened beyond Windows
  loopback.
- The upstream plugin build has no token.
- Input is `canvas.dispatchEvent`, so the user's real mouse and keyboard focus are not used.

```
M=tools/multiclient/desktop-devtools-mcp.sh
$M --call tools/list                                  # 47 tools
$M --tool get_client_state '{}'                       # gameState, worldX/worldY/plane, camera, ...
$M --tool walk_to '{"x":3218,"y":3222,"plane":0}'
$M --tool list_players '{}'                           # shows the Android "tester" when both are near
$M --tool type_chat '{"text":"::tele 3222 3218 0"}'
$M --tool screenshot '{}'                             # PNG (base64) of the canvas
```

### A/B recipe (desktop vs Android)

1. Start both clients:
   - desktop: `desktop.sh start && desktop.sh login`;
   - Android: under the device lock (`tools/device-lock.sh`), `tools/devtools-mcp.sh`.
2. Put both characters in the same state, with the same commands on each client (`type_chat` on
   both):
   - `::quest cook reset` or `::quest cook <stage>`;
   - `::empty`, `::item <id> [n]`;
   - `::tele x z 0`.
3. Install the plugin under test.
   - Desktop: the Plugin Hub in the isolated client (it runs the real 1.13.2). The hub UI is in
     RuneLite's Swing sidebar, which the MCP tools can't reach, so install by config instead:
     `desktop.sh stop`, append `runelite.externalPlugins=<internal-name>[,<more>]` to
     `.rlcustom\profiles2\default-*.properties`, then `start` and `login`. RuneLite downloads the
     current hub build into `.rlcustom\plugins\` ("Loading external plugin" in the log). Quest
     Helper was installed this way on 2026-10-17 (gen 28).
   - **Don't sideload plugins for an A/B.** Jars in `C:\osrs-test\home\.rlcustom\sideloaded-plugins`
     can't load RuneLite client resources in this RSProx-launched client. Quest Helper's
     `/util/clue_arrow.png` failed ("Failed to load image ... path: /util/clue_arrow.png"), so every
     ObjectStep threw in startUp, and the overlay and highlights disappeared at the first object step.
     The hub build loads it.
   - Version caveat: the hub serves its current build, which can be newer than the jar Android runs
     (2026-10-17: hub QH 4.18.0 vs `re/hub/quest-helper.jar` 4.17.1). Record both versions with an A/B.
   - The desktop log `C:\osrs-test\home\.runelite\logs\client.log` is ISO-8859 with CRLF. Use
     `grep -a`, or plain grep treats it as binary and prints nothing. Plugin exceptions show as
     "Uncaught exception in event subscriber".
   - Android: the host build.
4. Step through in lockstep. At each step capture the following on both clients:
   - `screenshot`;
   - `get_widget`/`dump_interface` for the plugin panel or overlay widgets;
   - `get_var` for the quest varp/varbits;
   - `list_npcs`/`list_objects` for highlight targets.
5. The desktop RSProx transcript in `C:\osrs-test\home\.rsprox\binary` has every server packet.
   Transcribe it with RSProx's `transcribe` CLI.

## Steps for the user (Windows)

You need:
- Docker Desktop running, with `osrs-openrune`, `osrs-openrune-http` and
  `osrs-openrune-http-desktop` up;
- RSProx on Windows (step 1);
- RuneLite: RSProx downloads and patches its own copy, so no separate RuneLite install is needed.
  `RuneLiteSetup.exe` in the repo is only for playing on the official servers.

1. **Install RSProx.** Pick one:
   - **Installer (simplest):** `RSProxSetup.exe` from https://github.com/blurite/rsprox/releases
     (v1.0). It bundles a JDK, and its launcher auto-updates RSProx to the latest version, which has
     rev-241 support.
   - **From source** (no prebuilt binaries): install a JDK 11+ (e.g. Temurin 21), then in PowerShell:
     ```
     git clone https://github.com/blurite/rsprox.git; cd rsprox; .\gradlew.bat proxy
     ```
     The same checkout builds in Docker here (`re/rsprox`, `eclipse-temurin:11`, `./gradlew assemble`
     passes). The GUI itself has to run on Windows.
2. **Add our server as a proxy target.**
   - In WSL: `tools/multiclient/make_proxy_target.sh`. It rewrites
     `tools/multiclient/proxy-targets.yaml` with the modulus the server serves.
   - Copy it into your RSProx folder (PowerShell):
     ```
     mkdir -Force $env:USERPROFILE\.rsprox
     copy \\wsl.localhost\Ubuntu\home\alex\osrs-plugins-reverse\tools\multiclient\proxy-targets.yaml $env:USERPROFILE\.rsprox\
     ```
   - If you already have a `proxy-targets.yaml`, append our entry to its `config:` list instead.
     Alternatively, use the **+** button next to the Proxy Target selector to import the file.
   - Restart RSProx after adding the file.
3. **Launch.** In RSProx, **New Session**:
   - Client Type: **RuneLite**;
   - Proxy Target: **OpenRune Test**;
   - Account: **Default** (never a linked Jagex account);
   - then **Launch Session**.

   The first launch downloads and patches RuneLite 1.13.2, which takes some tens of seconds. Allow
   the Windows firewall prompt for Java on private networks.
4. **Log in.**
   - On the RuneLite login screen choose **Existing User** (the classic username/password form, not
     "Jagex account").
   - Enter `desktop` and `desktop`. The account is created on this first login. You arrive in
     Lumbridge on world 255 with the normal desktop (resizable) interface.
   - RSProx shows the session's packets live.
5. **Quest Helper on desktop.**
   - In RuneLite: wrench icon (Configuration) → **Plugin Hub** at the bottom → search
     "Quest Helper" → **Install**.
   - Its icon (a question-mark book) appears in the sidebar.
   - The Plugin Hub works because this is the real RuneLite 1.13.2, which is still the current
     version. RSProx only redirects the game connection.
6. **Android at the same time.**
   - Sign in on the BlueStacks app as usual (it is `tester`).
   - Teleport both characters next to each other, for example `::tele 3222 3218 0` on both. Each
     client should show the other player.

## Comparing desktop and Android

Use the same quest at the same stage on both characters and step through it in lockstep. Only the
quests OpenRune implements work end to end: Cook's Assistant, Sheep Shearer, Romeo & Juliet. OpenRune has a few
others, such as Misthalin Mystery, that have not been tried here.

1. Reset or set the stage on both characters:
   - `::quest cook reset`, or e.g. `::quest romeo 30` (name prefix or quest id, then
     `start|done|reset|<stage>`);
   - `::item <id> [n]` / `::empty` for the same inventory;
   - `::tele x z [level]` to the same tile;
   - `::freeze`, so that NPCs stand still for screenshots.
   `::freeze` is world-wide, so turn it off again when you are done.
2. Open the same helper in Quest Helper on both. On Android:
   `adb shell setprop debug.osrsrl.sbclick '"Sheep Shearer"'`, or tap it in the RL panel.
3. For each step, compare:
   - the step text;
   - requirement colours (items green/red);
   - which NPC, object or ground item is highlighted;
   - the dialogue option highlighted in a chatmenu;
   - minimap and world arrows;
   - widget/inventory highlights.
   Take screenshots of both at the same moment.
4. Event and state diffs: the desktop RSProx transcript has every server message the desktop got
   (varps/varbits, zone updates, interfaces). Compare it with what the Android host saw for the same
   step: the host's event log, or `getVarps` through the plugin host.
5. Same account state on both clients (optional):
   - Desktop can log in as `tester` / `android-local` while the Android app is signed out.
   - This shows exactly the same save on desktop. Only one session per account can be online.

A difference in step order or text with the same varps points at the host's varp/varbit or event
plumbing. A highlight on the wrong tile with the right step points at the host's projection or
coordinate base.

## Troubleshooting

| Symptom | Cause / fix |
|---|---|
| RSProx: "missing a linked world" or the client hangs at "Connecting to server" | The target must use the **8082** jav_config (world host 127.0.0.1). The 8080 one advertises 10.0.2.2, which Windows cannot reach. Check `curl http://127.0.0.1:8082/jav_config.ws` from PowerShell and that `osrs-openrune-http-desktop` is running |
| Server log "Invalid RSA check" | Wrong modulus in the target. Rerun `make_proxy_target.sh`, copy the file again, restart RSProx |
| "Error connecting to server" | `osrs-openrune` down or still starting (ready line "OpenRune Server Successfully initialized"); check port 43594 |
| "Your account is already logged in" | The previous session is still in the world (for up to about a minute after a disconnect), or the same account is on the other client |
| "RuneScape has been updated" / login response 6 | RuneLite is not rev 241. Keep `runelite_bootstrap_commithash` (1.13.2) in the target |
| Plugin Hub empty or "outdated client" | A pinned RuneLite much older than the current release can lose Plugin Hub builds. Sideload the Quest Helper jar with `--developer-mode`, or update the pin |
| Desktop sees no doors opening or ground items | It would mean a desktop zone-update regression in `openrune.patch`. Rerun the headless check; the zone packets must decode |
| The Android app stops working | Desktop work never restarts `osrs-openrune` or `osrs-openrune-http`. If it happened, the container restart needs the device-lock protocol (`tools/device-lock.sh`) |

The old fallback server (`server/`, `osrs-rsps-test`) is Android-only. Its `World.kt` sends zone
updates only to Android players, so use OpenRune for multi-client.

## Files

| Path | What |
|---|---|
| `openrune/docker-compose.yml` | `http-desktop` service (`osrs-openrune-http-desktop`, :8082, world host 127.0.0.1) |
| `tools/multiclient/desktop_login.py` | headless desktop login check |
| `tools/multiclient/make_proxy_target.sh` | writes `proxy-targets.yaml` from the live `/rsa` |
| `tools/multiclient/proxy-targets.yaml` | RSProx target "OpenRune Test" (rev 241.1, port 43594, RuneLite 1.13.2 pin) |
| `tools/multiclient/desktop-build.sh` | builds RSProx + headless launcher (Docker) and stages the isolated `C:\osrs-test` home |
| `tools/multiclient/desktop.sh` | start / login / relogin / status / log / logout / stop of the isolated desktop client |
| `tools/multiclient/headless/OsrsTestLauncher.java` | headless RSProx session launcher with the OpenRune-only guard |
| `tools/multiclient/headless/export-runtime.init.gradle.kts` | Gradle init script exporting RSProx's runtime classpath |
| `tools/multiclient/desktop-devtools-mcp.sh`, `desktop_devtools_mcp.py` | MCP stdio bridge to the desktop Developer-Tools server (via Windows curl.exe) |
| `re/rsprox` (git-ignored) | RSProx checkout (blurite/rsprox, MIT), builds in Docker with `eclipse-temurin:11` |
