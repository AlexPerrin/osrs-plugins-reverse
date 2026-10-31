# osrs-plugins-reverse v0.1.0 (release notes draft)

Draft by client-qa for the orchestrator, from master 09c05c9 (release gate record: qa/client-0.1.0.md, PASS).
Not published; the orchestrator edits and publishes it after the user's OK.

## What this is
RuneLite plugins running inside the official Old School RuneScape Android app (build 241.4). The release APK is the
official app repacked by `tools/repack.sh`:
- `libosrsrl.so` (native/) is loaded right after the game library. It hooks the game thread and embeds a Java plugin
  host (host/) that runs unmodified RuneLite plugins on Android's runtime.
- The core RuneLite plugins come from RuneLite client 1.13.1, bundled in the host. Plugin Hub plugins are built from
  source at pinned commits and loaded as separate jars from the app's `files/plugins/` folder.
- It's a test build for a private test server (OpenRune): it signs in only through the server's local provider
  (LOCAL_AUTH) and connects only to the server address baked in at build time.

**This build never contacts Jagex or RuneScape servers and can't log in to the real game.** Sign-in is "Sign in with
Email / Username" against the test server's local provider only. Never use a real Jagex account with it.

## Files
| File | Size | sha256 |
|---|---|---|
| `base.apk` | 13,192,132 | `1d5e5ab6ab383eaafb137560355b448d6274e90506c5c1452506675ba909c1c4` |
| `split.apk` (arm64-v8a split; installs as split_config.arm64_v8a.apk) | 13,321,060 | `5bf4eb8201da67b090e5dae16f8e8dd49b15aca76a8d182b6f74a4083817f197` |

Built for: game server `10.0.2.2:43594`, sidecar `10.0.2.2:8080` (the Android emulator's address for the host machine),
the OpenRune login RSA public modulus, LOCAL_AUTH.

Signing certificate: self-signed debug key `CN=osrsrl` (`re/debug.keystore`, not in the repository), valid until 2054,
cert SHA-256 `49:8B:40:7E:E4:33:9E:CC:F1:3B:75:30:83:16:22:E9:6D:1D:55:BB:02:84:83:4C:E4:5A:9A:1D:59:C1:63:97`.
Android only accepts updates signed with the same key, so later releases must be signed with the same keystore (keep
a backup of it), and installing over the Play Store app needs an uninstall first.

### What's Jagex's and what's ours
| In the APK | Origin |
|---|---|
| base.apk: classes.dex, classes2.dex | Jagex's code, with our smali patches: osrsrl `loadLibrary` call; Singular starter disabled; consent-sync POST disabled; LOCAL_AUTH (AppAuth https precondition and issuer-https check relaxed) |
| base.apk: AndroidManifest.xml | Jagex's, plus `firebase_analytics_collection_deactivated=true` and `firebase_crashlytics_collection_enabled=false` |
| base.apk: res/xml/network_security_config.xml | Jagex's, with cleartext allowed (LOCAL_AUTH's plain-HTTP provider) |
| base.apk: other res/, assets/ | Jagex's, unchanged (resources re-encoded by apktool) |
| split: lib/arm64-v8a/liblibs.hal.system.osclient.so | Jagex's game library with patched bytes: consent-sync URLs zeroed, group score host and runescape.com web domain moved to `.invalid`, login RSA modulus, jav_config/slr.ws URLs, game port |
| split: libcrashlytics*.so, libdatastore_shared_counter.so | Jagex's, unchanged |
| split: lib/arm64-v8a/libosrsrl.so | **Ours**: native/ (hooks, game bindings, overlay) + Dear ImGui (MIT) + the embedded host.jar |
| host.jar (inside libosrsrl.so) | **Ours**: host/ (plugin host, Android AWT/Swing shims), plus third-party: RuneLite client subset and runelite-api 1.13.1 (BSD-2), Guice, Guava, Gson, OkHttp, Okio, commons-lang3/text, javax.inject (Apache-2.0), protobuf-javalite (BSD-3), slf4j (MIT), jsr305 (BSD), aopalliance (public domain) |
| META-INF signature | Ours (debug cert above) |
| Plugin jars (core marker jars, Plugin Hub jars) | **Not in the APK**: pushed separately to `files/plugins/` (hub plugins BSD-2, each jar keeps its LICENSE) |

Licence texts for the bundled third-party code are not in the APK; ship them with the release (NOTICE file).

## Build and install
```
host/build.sh                                  # re/host/host.jar
cmake -G Ninja -S native -B re/build -DCMAKE_TOOLCHAIN_FILE=$NDK/build/cmake/android.toolchain.cmake \
      -DANDROID_ABI=arm64-v8a -DANDROID_PLATFORM=android-26 -DCMAKE_BUILD_TYPE=Release
ninja -C re/build                              # libosrsrl.so, host.jar embedded
OPENRUNE=1 SERVER=10.0.2.2:8080 GAME_PORT=43594 LOCAL_AUTH=1 tools/repack.sh [--install]
```
Inputs (not in the repository): the official 241.4 `base.apk` and `split_config.arm64_v8a.apk`, RuneLite 1.13.1 jars in
`re/runelite/`, NDK r27d, apktool, and the build-tools zipalign/apksigner. Every build goes through `tools/repack.sh`,
which applies the privacy patches and prints 7 `telemetry:` lines.

Install (both files together):
```
adb install-multiple -r base.apk split.apk
```
Plugins: copy the dexed jars to `/sdcard/Android/data/com.jagex.oldscape.android/files/plugins/`. The default set is
grounditems, opponentinfo and quest-helper. Hub plugins are built with `host/plugins/hub/build.sh <name>` →
`re/host/plugins-extra/hub-<name>.jar`.

## Privacy
- Telemetry patches (every build): Singular attribution never starts; the consent-sync POST to account.jagex.com is
  disabled and its URLs zeroed in the game library; Firebase Analytics deactivated and Crashlytics collection off;
  group-ironman score lookups and runescape.com web links go to the reserved `.invalid` TLD.
- Network QA at this commit, with only the expected hosts seen: the test server (10.0.2.2), api/static.runelite.net
  (the host's read-only GETs), Braze and OneTrust (both left on by the user's decision), one Google address, and the
  system's dns.google check. Nothing to Jagex, RuneScape, Singular or app-measurement.
- Plugin networking: the host's OkHttp client is guarded (NetGuard). Plugin jars that use java.net directly are
  refused at load unless vetted (R1 NetScan; vetted: loot-lookup, devtools).
- The APK was scanned for secrets and personal data: none found. It contains only the test server's public RSA modulus.

## Plugins (tested at this commit on the Android emulator against OpenRune)
- Core RuneLite plugins: 90, all start; 89 run, 1 starts (World Hopper: its panel lists worlds, hopping isn't mapped
  and must never be used).
- Plugin Hub plugins: 107 (Quest Helper + 106 pinned), loaded together with all core plugins: 77 run, 29 start
  (feature not exercised, or blocked by test-server content), 1 fails to start (Drop Sounds: needs javax.sound).
- The per-plugin table is docs/plugin-host.md (status as of this release: qa/client-0.1.0.md section 8).

## Known gaps
1. Chat Enter: once the soft keyboard has been shown in a session, Enter only submits chat while it is shown.
2. Plugins can reorder and rewrite the native long-press menu but can't remove entries (No Examine has no effect).
3. Current World: the in-game world switcher title isn't checked; World Hopper can't hop.
4. Ground item despawn timers read 0 on the test server (Clue Juggling Timers, Ground Items' despawn timer).
5. Random Event Hider can't be staged on the test server.
6. Decimal Prices' conversion is unconfirmed (no `.` key in the mobile quantity prompt).
7. Time Tracking's farming completion check throws (`ConfigManager.getRSProfiles` missing in the host), so its
   completion notifications don't work; the farming tab works.
8. Drop Sounds fails to start (desktop audio API).
9. With all ~200 plugins loaded the client drops to ~11 fps during login, then runs at 30 fps.
10. No GPU plugin paths (`isGpu` is false), so GPU-only features are off.
