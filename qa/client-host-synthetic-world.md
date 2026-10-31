# QA: client/host-synthetic-world
- Branch: `client/host-synthetic-world`
- Commit: `ee537b4c99d67b88cdf15fea5696917d01f417b8`
## Verdict: **PASS**

Merge gate, client-qa, 2026-10-18 20:32-20:55. Change (client dev gen 42, on master b9749a6, without the gen 41
BufferedImage merge b773f74): a host `net.runelite.client.game.WorldService` replacing RuneLite 1.13.1's (same
api.runelite.net fetch; `getWorlds()` adds the current world when the fetched list lacks it, built from the game's
own slr.ws world list entry, else from the client's id/flags/host fields; if the fetch failed it returns just the
current world), `net.runelite.host.CurrentWorld`, `Native.worldEntry` / `game::world_entry` reading the world list
global at ELF `0xace6c0`; docs rows in offsets.md, plugin-host.md, client-state.md, backlog.md.

## Build (at the commit, clean tree)
- `host/build.sh` rc 0 (host.jar 7990228 bytes, sha256 00d418c6...; `WorldService.class` and `CurrentWorld.class`
  in `re/host/classes`), `ninja -C re/build` rc 0, no warnings (libosrsrl.so sha256 bc78dd9f...).
- `OPENRUNE=1 SERVER=10.0.2.2:8080 GAME_PORT=43594 LOCAL_AUTH=1 tools/repack.sh --install`: rc 0, **7 `telemetry:` lines**
  (Singular starter, consent sync POST, Firebase/Crashlytics manifest flags, 2 consent/sync URLs zeroed, group score
  host, runescape.com web domain); LOCAL_AUTH network security config and AppAuth smali patched; RSA modulus,
  jav_config, slr.ws and game port patched. Installed base/split sha256 (e81f8b17..., 6b74af55...) match re/out.

## Device regression (Android emulator, OpenRune, LOCAL_AUTH sign-in "Tester", slot `qa`)
Plugins: the default 3 + `hub-current-world` (the pinned gen 40 jar) + `worldhopper` (marker jar, with
`worldhopper.ping/displayPing/menuOption=false` so nothing pings Jagex worlds) + `devtools`. All 6 started both runs;
no exception from Current World, WorldService or World Hopper (only the known FlatDarkLaf/Guice and GL emulator lines).

| Check | Result |
|---|---|
| Current World start (the fix) | PASS: `started plugin Current World`, no NPE; host log `current world 255 from the world list: host 10.0.2.2, activity "Test server", flags 0x1, players 0, location 0` (both app starts) |
| "World 255" under the minimap | PASS: drawn after login and after the `::drop` relog (CONNECTION_LOST -> LOGGED_IN, no exception) |
| Overlay activity | PASS: with `currentworld.overlayActivity=true` (app restart) the overlay reads "World 255 / Test server" |
| World Hopper | PASS: its World Switcher panel lists `255 0 Test server` (highlighted as current) above the api list (301 Trade - Free, 302 Trade - Members, 303, 304 Trouble Brewing ...); no hop was made |
| Native read vs slr.ws reload | PASS: with World Hopper calling `getWorlds()` every second on its executor (pingCurrentWorld calls it before its config check), the login-screen world switcher was opened and closed 7 times; the game kept running, no crash |
| Other default plugins | PASS: Opponent Information ("Man 0/7" box while fighting a Man), Ground Items (drop label with GE/HA prices), Quest Helper started; ::tele, tabs, walking normal |
| LOCAL_AUTH sign-in | PASS: Sign out -> "Sign in with Email / Username" -> "Play Now / Tester" -> LOGGED_IN (no Jagex screen) |

Not checked: the in-game world switcher title (69:2, `worldSwitcherActivity`): the logout tab didn't open from
the tab strip, as for the dev. The "api fetch failed" branch (returns only the current world) was reviewed in code,
not forced on device. Desktop A/B not run: no drawing code changed (the overlay is the plugin's own; desktop against
world 255 has the NPE this fixes).

## Native claim (reverse, read-only)
Headless Ghidra `-readOnly` from a scratch dir (`Decompile.java 0x5320dc 0x532748`, `Xrefs.java 0xbce6c8 0xbce6d0 0xb82518`;
Ghidra = ELF + 0x100000). My decompilations are byte-identical to the dev's `re/decomp/005320dc.c` and `00532748.c`.
- slr.ws parser (ELF `0x4320dc`): count u16 -> `0xace6b8`, stored as u64 at `0xace6c8`; `World**` array at `0xace6d0`
  (memset 0, then one 0x68-byte World per entry): `+0x00` id (u16), `+0x04` flags (i32), `+0x08` players stored as int
  (`(int)(short)`, so the native int32 read is right; offsets.md's "i16" is the wire type), `+0x10` host and `+0x28`
  activity as 24-byte libc++ strings (SSO flag `+0x27`/`+0x3f`), `+0x40` location (u8 stored as uint), `+0x60` index.
  Matches `game::world_entry`.
- setWorld (ELF `0x432748`): id -> Client `+0x3174`, flags -> `+0x2f64`, host string `+0x10` -> Client `+0x3098`
  (`plVar11 + 0x613`), members bit -> `+0x2070`, ports `+0x30b0/+0x30b4`. Matches the fallback path and offsets.md.
- Xrefs: `0xace6c8`/`0xace6d0` and the GOT slot `0xa82518` are read by the sort `0x4782c8`, the switcher drawing
  `0x4233b0` and the login pick `0x41615c` (count), as offsets.md says.
- Live: the local sidecar's `/slr.ws` is `00 01 | 00ff 00000001 "10.0.2.2" "Test server" 00 0000`, which is what the
  host logged.

## Network QA (2026-10-18 20:37:01-20:46:26)
Socket sampler on app uid 10071 (`/proc/net/tcp`+`tcp6`, ESTABLISHED, 915 samples), logcat cleared before app start.
Run: app start -> login-screen world switcher x7 -> Sign out -> LOCAL_AUTH sign-in -> Play Now -> World Hopper panel,
combat, `::tele` Varrock and back, tabs -> `::drop` relog -> force-stop + launch (overlayActivity) -> "Try again" ->
Play Now -> combat, teleport, tabs.
- netd DNS: `api.runelite.net` (3), `static.runelite.net` (2), `sdk.fra-01.braze.eu` (2), `dns.google` (1, at 20:40:00,
  right after the LOCAL_AUTH sign-in: the expected system Private DNS validation). No `*.jagex.com`, `*.runescape.com`,
  `*.x.invalid`, `singular`, `app-measurement`.
- App-uid sockets: 10.0.2.2:43594, 10.0.2.2:8080; 104.26.8.233, 172.67.74.139 (runelite); 104.18.35.7 (Braze);
  172.64.155.231 (OneTrust), all :443. Nothing else, no Jagex address. 0 `ConsentSync`/`Singular` lines.
- The new WorldService fetches only `https://api.runelite.net/runelite-1.13.1/worlds.js`, as RuneLite's did; the
  current-world entry is read from game memory, nothing extra is fetched.

## Observations (not blocking)
- `game::world_entry` reads game memory with plain `memcpy` off the game thread (World Hopper's executor, every second
  while it runs). If the game re-parses slr.ws at the same moment, the old Worlds are freed under the reader. Not hit
  in 7 reloads; a client-thread read or a cached entry would remove the window.
- Host start waits ~10 s on `Failed to retrieve worlds on first run` when World Hopper is enabled: its `onWorldsFetch`
  calls `getWorlds()` on the fetch thread before `firstRunFuture` completes. Same code in RuneLite 1.13.1's
  WorldService, so not new in this change.

## Device restore
Test jars removed by name, `debug.osrsrl.devtools` 0, settings.properties pushed back from the pre-slot copy,
tester logged in at 3219,3222; `tools/device-restore-check.sh check /tmp/osrs-slot/qa`: all OK. Lock freed.

Evidence: /tmp/claude-1000/-home-alex-osrs-plugins-reverse/a0087aa1-b8d3-439d-add9-f4cbee8fc5e6/scratchpad/qa42/
(build/repack logs, q1-q21.png, logcat-full.txt, sock.txt, ghidra/run.log).
