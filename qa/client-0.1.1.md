# QA: v0.1.1
- Branch: `master`
- Commit: `7bb7048cec54292401799ea452d8cd6668a09598`
## Verdict: **PASS**

Release gate for `v0.1.1` (batch `rel-20261022-1`, tag chosen by the user on 2026-10-21), client-qa `cq3`, 2026-10-21 from
20:40 EDT, on master at 7bb7048 in a detached worktree (`re/harness/wt/cq3`; the main checkout's `re/` entries symlinked
except `re/build`, `re/host`, `re/out`, which are worktree-local; `base.apk` and `split_config.arm64_v8a.apk` symlinked).
Master had moved to ea7d7df by then: every commit after 7bb7048 touches only the harness (`tools/harness`, `tools/hooks`,
`.claude/agents`, harness docs), no client code (`git diff --name-only 7bb7048..ea7d7df`). The 10 changes of the batch:
client-gpu-117hd-feasibility, host-config-rsprofiles, host-javax-sound-shim, host-menu-entry-removal, hub-batch-next,
hub-fish-barrel-check, offsets-slot-6-note, poison-sprite-override, rebuild-double-logged-in, scene-item-despawn; their
merges 4a572ce, 788b384, 6d82e5d, 0c7431b, 77c4aa3, f01f851, faac757, 7ec40ca, 5c0a8dc are all ancestors of 7bb7048
(`git merge-base --is-ancestor`); client-gpu-117hd-feasibility has no commit (see "Release notes").

## 1. Build at the commit
- `host/build.sh`: rc 0, `re/host/host.jar` 7,995,870 bytes, sha256 `901f4c9e391a5162b434c11f055b1cb8674c6145aba50813338195d3ea63e214`.
  The four `awt shim missing` lines and the five `unresolved ... FatalErrorDialog` lines are the build's usual report.
- Fresh `cmake -G Ninja` (NDK r27d at `~/Android/Sdk/ndk/android-ndk-r27d`, arm64-v8a, android-26, Release) +
  `ninja -C re/build`: rc 0, 24/24, 0 warnings. `libosrsrl.so` 9,662,272 bytes, sha256
  `583239602c48713bef807714a0ca1d9549a16f3efcac6218bed7cacb11bbad14`; host.jar embedded byte-identical (offset 321600).
- `OPENRUNE=1 LOCAL_AUTH=1 MODULUS=<openrune.sh modulus> tools/repack.sh`: rc 0.

## 2. Privacy patches in the repack log
All 7 `telemetry:` lines present (repack.log lines 4-11); every one-match assertion in `patch_telemetry.py` holds (each
exits non-zero on a count other than one; see section 9 for the assertion lines):
```
telemetry: Singular starter disabled in smali/sa/b.smali
telemetry: consent sync POST disabled in smali/za/l.smali
telemetry: manifest firebase_analytics_collection_deactivated=true, firebase_crashlytics_collection_enabled=false
telemetry: zeroed https://account.jagex.com/consent/sync (1x)
telemetry: zeroed https://account.development.jxp.jagex.com/consent/sync (1x)
telemetry: group score host -> pp-%s.%s.x.invalid
telemetry: web URL domain runescape.com -> x.invalid
```
LOCAL_AUTH patches applied (`res/xml/network_security_config.xml`, `smali/ad/b.smali`, `smali/net/openid/appauth/n.smali`),
patch_server.py (`patched RSA modulus at file offset 0xd8ebc`, `/jav_config.ws at 0xdcabb`, `/slr.ws at 0xdec96`) and the
game port patch (`43594 -> 43594 at file offset 0x432890`).

## 3. Release APK
| File | Size | sha256 |
|---|---|---|
| `re/out/base.apk` | 13,192,132 | `5b8bcccfcd21c2e5ccf3d3691961bfeef04793124c1f08fe2f32ea52a0f96297` |
| `re/out/split.apk` (installs as split_config.arm64_v8a.apk) | 13,325,156 | `be4875adc52ebccbd255adcf293dd134015625b067edfbc7531a0d0a5cd653ab` |

Signer (`apksigner verify --print-certs`): `CN=osrsrl`, cert SHA-256
`498b407ee4339eccf13b7530831622e96d1d55bb0284834ce45a9a1d59c16397` (re/debug.keystore, as v0.1.0).

## 4. Secrets and personal data in the release APK
Scanner `secretscan.py` (my scratch dir, same patterns as the v0.1.0 gate: PEM/OpenSSH/DER keys, JKS magic, e-mail
addresses, `C:\Users\`, `/home/`, `/mnt/<drive>/`, the user's name, GitHub/AWS/Slack/OpenAI/Anthropic/Bearer/JWT/Google
tokens, adb/emulator/BlueStacks ids, the tester account names, hex runs of 128+ and decimal runs of 150+) over the
ASCII and UTF-16 strings of every zip entry of `re/out/base.apk`, `re/out/split.apk` and `re/host/host.jar`, keeping only
matches absent from the official 241.4 `base.apk` + `split_config.arm64_v8a.apk`. 15 matches, 10 also in the official
APKs, 5 new:
- three e-mail addresses in RuneLite's own GPU shader and logback licence headers inside host.jar (`frag.glsl`,
  `hybrid.glsl`, `xbr_lv2_*.glsl`, `logback.xml`: upstream authors' headers, part of the RuneLite client jar);
- one 128+ hex run in the split's `liblibs.hal.system.osclient.so`: the OpenRune test server's RSA public modulus,
  patched in by `patch_server.py` (the public key, not a secret);
- the 4-byte `\xce\xce\xce\xce` JCEKS magic in host.jar and libosrsrl.so: a false positive (no keystore structure).
No key, token, e-mail of the user, user name, local path, device id or account name. **PASS.**

## 5. LOCAL_AUTH sign-in
Device slot 21:18-21:31 EDT (BlueStacks Pie64, 127.0.0.1:5555, app uid 10071; the release APKs installed with `adb
install-multiple -r`, `pm path` + `sha256sum` on the device match section 3's hashes). On the "Play Now / Tester" screen
(screenshot `login-playnow.png`, taken only to locate the sign-in buttons, as docs/client-state.md "Login" requires) I
tapped Sign out, confirmed Yes, saw only "Sign in with Google" and "Sign in with Email / Username"
(`login-signed-out.png`) and tapped **only "Sign in with Email / Username"**. The AppAuth activity
(`net.openid.appauth.AuthorizationManagementActivity`, logcat 21:19:57) returned straight to "Play Now / Tester"; Play Now
gave `GameStateChanged LOGGING_IN` -> `LOADING` -> `LOGGED_IN` at 21:20:28 (`logcat-run1-host.txt`), player at 3226,3215.
No Jagex login page was shown. **PASS.**

## 6. Network QA (docs/app-telemetry.md "Network QA")
Window 21:18:49-21:28:21 (`adb logcat -c` at 21:18:47; sampler: ESTABLISHED uid-10071 rows of /proc/net/tcp{,6} every
0.5 s, 3,368 samples, `sockets.txt`). Build: this commit's APK. Run: app start (default 3 plugins + devtools), Sign out,
"Sign in with Email / Username", Play Now, LOGGED_IN, `::tele 3210 3424 0` (a scene rebuild), `::drop` (CONNECTION_LOST
21:21:25 -> LOGGED_IN 21:21:26), force-stop + launch at 21:24:24 with all 219 core and hub jars pushed, Play Now,
LOGGED_IN 21:25:51, `::varp`, `::tele` home, about two minutes in game.
- netd DNS (`asked for`): `api.runelite.net` x2, `static.runelite.net` x2, `sdk.fra-01.braze.eu` x2. No `dns.google`
  this time.
- App sockets (distinct): 10.0.2.2:43594 (game, whole window); 104.26.8.233 and 172.67.74.139 :443 (runelite);
  172.64.152.249 and 104.18.35.7 :443 (Braze); 104.18.32.25 and 172.64.155.231 :443 (OneTrust); and, only from
  21:24:56, after the hub batch started, **185.199.108.133:443** (GitHub, raw.githubusercontent.com range: the approved
  C Engineer: Completed sound GETs, NET-OKHTTP through NetGuard, as in the v0.1.0 gate's extra run; hub plugins are not
  in the APK). The sidecar's short-lived 10.0.2.2:8080 connections fell between samples. No Google address this run.
- Must-not-appear: 0 lines matching `jagex.com`, `runescape.com`, `singular`, `app-measurement`, `x.invalid`,
  `ConsentSync` in the whole logcat; no socket to a Jagex address. **PASS.**

## 7. R1 NetScan (off-device)
`host/plugins/hub/netscan-check.sh` on the main checkout's 219 jars in `re/host/plugins-extra/` and the 4 in
`re/host/plugins/`: self-test ok, 223 `load`, no `REFUSE` (loot-lookup and devtools through the vetted list). **PASS.**

## 8. Plugins (core + all pinned hub plugins)
One run with every jar in the main checkout's `re/host/plugins-extra/` (89 core marker jars, 130 pinned hub jars =
`host/plugins/hub/plugins.txt`'s 130 pins, devtools) plus the default 3: **222 `started plugin` lines, 222 distinct, in
the new process (pid 816)**, LOGGED_IN at 21:25:51 (`logcat-run2-host.txt`). One plugin failed to start:
- **Sailing** (jar `hub-barracuda-trials-pathfinder`'s second plugin, hub-batch-next): `E osrsrl-host: plugin
  com.duckblade.osrs.sailing.SailingPlugin failed to start`, `NoSuchMethodError: No static method
  toUnmodifiableSet()Ljava/util/stream/Collector; in class Ljava/util/stream/Collectors;` at
  `PrioritizeCargoHold.<clinit>(PrioritizeCargoHold.java:34)` (Java 10 API, absent on API 28; the CompatRewrite
  doesn't cover it). **Correction** for docs/plugin-host.md's "Barracuda Trials Pathfinder / Sailing" row: Sailing
  `throws`; Barracuda Trials Pathfinder started. Not a regression of a release feature (server has no sailing either).
Exceptions in the run, excluding the known FlatDarkLaf and WorldService lines: only Idle Alarm's documented
`NullPointerException`s in `IdleAlarmPlugin.onClientTick` (lines 45 and 60: `Instant.plus` and
`Player.getIdlePoseAnimation()` on null), 5,265 of them until the player first animates, as its row says ("desktop
does the same"); World Hopper's JNA `ClassNotFoundException`s (ping off, known). Nothing else.
Changed paths at this commit:
- **rebuild-double-logged-in / scene-item-despawn**: each `::tele` posted exactly one `GameStateChanged LOADING (scene
  rebuild)` + one `LOGGED_IN` (21:21:05, 21:27:46); 0 `already posted` lines; `::drop` posted CONNECTION_LOST then one
  LOGGED_IN.
- **poison-sprite-override**: with all plugins loaded, `::varp 102 6` -> `dump_interface 160`: the HP orb icon 160:17
  (component 10485777) shows `spriteId 1067` (the poisoned heart) while 160:11 keeps 1060; `::varp 102 0` afterwards.
  The host's `sprite override 1067: ... cropped` log line was not in the logcat buffer I saved (the buffer had filled
  with Idle Alarm's traces by then); the dev's gen 46 evidence stands for the crop itself, this run shows the override
  path loads with the whole batch without a refusal line (0 `doesn't match` lines).
- **host-javax-sound-shim**: no `javax/sound` `NoClassDefFoundError` with the batch (Drop Sounds' pin is dropped, so no
  Drop Sounds jar); **host-config-rsprofiles**: no `getRSProfiles` `NoSuchMethodError` (Time Tracking started, its
  completion check ran during the two minutes); `rsprofile.rsprofile.default.type=STANDARD` was written to
  settings.properties at login (restored afterwards). **host-menu-entry-removal**, **hub-fish-barrel-check**,
  **hub-batch-next**'s Camera Smoothing: started with the batch (No Examine, Fish Barrel, Camera Smoothing all in the
  222); their features were not re-exercised here (dev evidence in `re/evidence/host-menu-entry-removal/`,
  `re/evidence/hub-fish-barrel-check/`, `re/evidence/hub-batch-next/`).
Plugin-host table: the row counts stand (core 89 runs / 2 starts; hub 86 runs / 32 starts / 2 excluded) with the
Sailing correction above; the Herbi AFK row says `runs` but its note only records a gen 44 run that drew nothing
(docs/plugin-host.md:224): the dev should settle that row.

## 9. Native claims (`reverse`)
Checked at the source (re/osclient.so, re/osclient.dis, readelf; a subtask ran the sweep, I re-read the cited lines):
- **Client vtable slot 6 = draw `0x421a20`** (docs/offsets.md, offsets-slot-6-note): `readelf -rW` relocations
  `a6a008 -> 411640` (slot 5, doCycle), `a6a010 -> 421a20` (slot 6 = vtable `0xa69fe0` + 0x30). In `0x73bfb8`:
  `73c058 ldr x8,[x8,#0x40]; 73c05c blr x8` (slot 8) then `73c070 ldr x8,[x8,#0x30]; 73c074 blr x8` (slot 6), in that
  order, on the object `0x400f40` passes (`400f6c bl 0x73bfb8`); the tick twin `0x400f10` -> `0x73bed8` calls that
  object's slot 5 (doCycle). **Confirmed.** Precision for the dev: `0x86bdc8` reaches `0x400f40` through its own vtable
  (`86c2a8 ldr x8,[x8,#0x100]; blr x8`, vtable `0xa69928` entry `a69a28 -> 400f40`), not a direct `bl`; the row could say
  "virtual".
- **Camera pitch/yaw targets `+0x8949c`/`+0x894a0`** (hub-batch-next): `native/src/game.cpp:772-776` writes
  `c + f_camera + 20 + 4*which` with `f_camera = 0x89488` (game.cpp:120), via `jni_bridge.cpp:1628 setCameraTarget` from
  `AndroidClient.java:844-845`. Game-side writer `0x43f3e8`: `43f3f0 mov w8,#0x949c; 43f3fc movk w8,#0x8,lsl#16;
  43f418 str d0,[x0,x8]` (pitch, yaw masked 0x3fff, fixed-point x8). **Confirmed.**
- **patch_server.py / patch_port.py bytes** in the unpatched osclient.so: original modulus string at `0xd8ebc`,
  `https://oldschool.config.runescape.com/jav_config.ws?m=0` at `0xdcabb`, `.../slr.ws?order=LPWM` at `0xdec96` (each
  NUL-terminated, `data.count(old) == 1`); `0x432890` = `0x5295494b` (`mov w11, #0xaa4a` = 43594), pair at `0x9f400`
  (43594, 443). **Confirmed.**
- **patch_telemetry.py match counts**: `patch_singular` lines 41-43/66-67 (one holder of "Singular Initialized", one
  starter), `patch_consent_sync` 71-73/97-98 (one holder, one task method), `patch_manifest` 158-160 (one tag each),
  `patch_native` 114-115 (consent URLs, n == 0 exits), 124-129 and 135-143 (exactly one group-score URL and one bare
  `runescape.com`). The repack log's `(1x)` lines and rc 0 show they held on this build.

## 10. Release notes (`pr-text-review`)
The daemon's release text (`tools/harness/hd/approvals.py:159`) is the tag and the batch's 10 change slugs. Nine name a
merge in the commit (header). **`client-gpu-117hd-feasibility` is not a change in this release**: the store entry is a
v2 migration (`harness/changes/client-gpu-117hd-feasibility.md`: stage live, commit null, log only "migrated from v2
tasks"); nothing in `v0.1.0..7bb7048` is its code or doc, and `isGpu()` still returns false (docs/plugin-host.md:148).
Drop it from the notes before publishing. (Not a build defect; see the verdict.)

## 11. A/B (desktop vs Android) for the plugins that draw differently since v0.1.0
**Not run: the desktop client would not start.** `tools/multiclient/desktop.sh start` twice (20:47 and 20:54) printed
`started RSProx pid ?` with no `rsprox.pid` and no `logs/rsprox-*.{out,err}` written, and `login` found no client (no
java.exe; the WSL interop error `UtilAcceptVsock accept4 failed 110` appeared in the same minutes). Tooling, not the
build. The drawing change of this release, Poison's heart, is covered by section 8's data check on Android (sprite 1067
on the orb icon at varp 102 = 6, the same sprite id RuneLite's plugin overrides on desktop); Camera Smoothing has no
overlay; the other changed rows are server-dependent counts. Desktop lease taken 20:46, freed 20:56.

## Slot
`tools/device-restore-check.sh pre /tmp/osrs-slot/cq3` at 21:17 (75 paths, settings.properties md5 166d9905...). Device
lease 21:17-21:31 and 21:33-21:34 (final check). The 219 pushed jars were removed by name (219 `adb shell rm` calls, no
wildcards), the plugin-created files and directories (`loots/`, `plugin-data/ground-item-sounds/*.wav`, `spam-filter/`,
`supplies-tracker/`) by name, `debug.osrsrl.devtools` cleared, the app restarted with the default 3 plugins, tester
LOGGED_IN (21:29:31) after `::tele 3219 3222 0`, settings.properties pushed back. `check`: all OK (plugins, debug props,
settings identical, no new files, LOGGED_IN, no local-auth file; MANUAL: player at 3219,3222, inventory untouched, no
item spawned, GE lobster offer kept). Lease released.

## For the orchestrator (before publishing)
1. **Release notes**: drop `client-gpu-117hd-feasibility` from the generated notes (section 10); the other nine slugs
   are true of 7bb7048. The notes could add the user-visible line: "v0.1.1: Poison's heart override, javax.sound shim,
   RS profiles for Time Tracking, native menu entry removal (No Examine), ground-item despawn on rebuilds, one LOGGED_IN
   per rebuild, 24 more hub plugins (hub-batch-next), Fish Barrel's Check".
2. **docs/plugin-host.md corrections** (a hook keeps me to records): Sailing `throws` (`Collectors.toUnmodifiableSet`,
   section 8); the Herbi AFK row's status vs its note.
3. **Desktop launcher**: `desktop.sh start` produced no RSProx pid or log twice (section 11); the A/B tooling needs a
   look before the next gate.
4. **Network QA record**: lives here (section 6); the network-qa skill asks for a dated heading in docs/app-telemetry.md,
   which I can't edit.
Evidence: `re/evidence/rel-20261022-1/` (repack.log, host-build.log, ninja.log, secretscan.py + .out, netscan.out,
sockets.txt, logcat-run1-host.txt, logcat-run2-host.txt, login-playnow.png, login-signed-out.png).
