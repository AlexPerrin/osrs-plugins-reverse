# QA: v0.1.0
- Branch: `master`
- Commit: `09c05c9fb6b8e206597f1ba13cd058822478e088`
## Verdict: **PASS**

Release gate for the first client release, `v0.1.0` (first official release candidate), client-qa, 2026-10-18 22:13-22:42, on
master at 09c05c9 in a clean detached worktree (main tree's ignored inputs symlinked: re/runelite, re/hub, re/hub-build,
re/hub-deps, re/hub-src, re/devtools-build, re/devtools, re/tools, re/debug.keystore, re/jadx, base.apk,
split_config.arm64_v8a.apk; build outputs re/host, re/build, re/out are worktree-local). Device slot `qa` (BlueStacks
Pie64, 127.0.0.1:5555, app uid 10071), OpenRune live server (old tree), LOCAL_AUTH account "Tester".

Every check passed. Two plugin rows are corrected (Drop Sounds, Time Tracking; see the table). Before publishing, see
"For the orchestrator" at the end (licence notices, the Jagex-APK rule in docs/workflow.md).

## 1. Build at the commit
- `host/build.sh`: rc 0, `re/host/host.jar` 7,990,267 bytes, sha256 `6f88e4a30d1a132d400b6e4eae8450aba71a2f10f4c966df3c563392fcac837d`.
  The four `awt shim missing: ...getSource()` lines are the build's usual report.
- Fresh `cmake -G Ninja` (NDK r27d, arm64-v8a, android-26, Release) + `ninja -C re/build`: rc 0, 24/24, no warnings.
  `libosrsrl.so` 9,659,552 bytes, sha256 `1ed6d429a3ef0b8f79396ba65e3ebfa9b3341bf0bb4c139f07aab51830dd2deb`; host.jar is
  embedded byte-identical (found at offset 321424).
- `OPENRUNE=1 SERVER=10.0.2.2:8080 GAME_PORT=43594 LOCAL_AUTH=1 MODULUS=<literal, from openrune.sh modulus in the main
  tree> tools/repack.sh`: rc 0.

## 2. Privacy patches in the repack log
All 7 `telemetry:` lines are present, and every one-match assertion in `patch_telemetry.py` passed (each exits non-zero
on a count other than one, so rc 0 means they held):
```
telemetry: Singular starter disabled in smali/sa/b.smali
telemetry: consent sync POST disabled in smali/za/l.smali
telemetry: manifest firebase_analytics_collection_deactivated=true, firebase_crashlytics_collection_enabled=false
telemetry: zeroed https://account.jagex.com/consent/sync (1x)
telemetry: zeroed https://account.development.jxp.jagex.com/consent/sync (1x)
telemetry: group score host -> pp-%s.%s.x.invalid
telemetry: web URL domain runescape.com -> x.invalid
```
The LOCAL_AUTH patches were also applied (network_security_config cleartext, the AppAuth https precondition, the
issuer-https check), along with the osrsrl loadLibrary injection and patch_server.py (RSA modulus at 0xd8ebc,
/jav_config.ws at 0xdcabb, /slr.ws at 0xdec96, port 43594 at 0x432890).

## 3. Release APK
| File | Size | sha256 |
|---|---|---|
| `re/out/base.apk` | 13,192,132 | `1d5e5ab6ab383eaafb137560355b448d6274e90506c5c1452506675ba909c1c4` |
| `re/out/split.apk` (installs as split_config.arm64_v8a.apk) | 13,321,060 | `5bf4eb8201da67b090e5dae16f8e8dd49b15aca76a8d182b6f74a4083817f197` |

The installed copies on the device (`pm path`, then `sha256sum`) match both hashes. Signer: `CN=osrsrl` (re/debug.keystore),
cert SHA-256 `49:8B:40:7E:E4:33:9E:CC:F1:3B:75:30:83:16:22:E9:6D:1D:55:BB:02:84:83:4C:E4:5A:9A:1D:59:C1:63:97`.

## 4. Secrets and personal data in the release APK (added to this gate by the orchestrator)
Scope: both APKs extracted, plus host.jar extracted from libosrsrl.so (the same bytes) and the apktool-decoded base
tree (smali/res/assets as built). The scanner (`secretscan.py`, session scratchpad) reads ASCII and UTF-16 strings and
reports only matches that are absent from the official 241.4 APK (base + split, raw and apktool-decoded). Patterns:
PEM headers, OpenSSH keys, DER RSA private keys (PKCS#1 and PKCS#8), JKS/JCEKS magic, email addresses, `C:\Users\...`,
`/home/...`, `/mnt/<drive>/...`, the user's name, GitHub/AWS/Slack/OpenAI/Anthropic tokens, Bearer tokens, JWTs, Google
API keys, adb serial/emulator/BlueStacks ids, the tester account names, hex runs of 128+ characters and decimal runs of
150+ digits.

Files that differ from the official base.apk/split_config.arm64_v8a.apk: AndroidManifest.xml, classes.dex,
classes2.dex, the resources (apktool re-encodes them; content unchanged apart from network_security_config), META-INF
(our signature), split liblibs.hal.system.osclient.so (patched bytes) and the new libosrsrl.so (with host.jar). Because
the scan reports only strings absent from the official APK, Jagex's own matches drop out: 3 PEM labels (OpenSSL's
"PRIVATE KEY" labels in the game lib and the public cacert*.pem bundles), the Google API key and the tester-like
strings. Only the differing files can produce a hit.

Result: **no secrets or personal data.**
- Private keys, keystores, tokens, JWTs: none.
- Public RSA modulus: present, as expected. It is the only long hex run not in the official app (the patched login
  modulus in liblibs.hal.system.osclient.so at 0xd8ebc, once); no private exponent-sized number anywhere.
- Emails (all public licence-header attributions in RuneLite GPU shader sources inside host.jar, not the user's):
  `Adam@sigterm.info` (RuneLite's author, also in logback.xml), `caseypzwicker@gmail.com` (hybrid.glsl),
  `sergiogdb@gmail.com` (xbr_lv2 shaders). `hw@s.Nt` in libosrsrl.so is random binary bytes. The user's email: absent.
- `/home/alex`: only in apktool's build intermediate `re/apk/base/build/resources.zip`, which is not in the APK (the
  extracted base.apk has no hit). No `C:\Users`, `/mnt/c` or adb serial in any shipped file.
- The Google API key and other identifiers that match are Jagex's own, already present in the official app.
- Not done: a direct search for the LOCAL_AUTH signing key's and the game RSA private key's own bytes. Reading
  openrune/src/.data/{game,client}.key and rsa.properties was denied by the permission classifier ("Credential
  Materialization"); I did not work around it. The structural checks above (PEM/DER/keystore/long-number patterns) found
  no private key material. LOCAL_AUTH adds no key to the APK (patch_local_auth.py only relaxes https checks).

## 5. LOCAL_AUTH sign-in
I took a screenshot first ("Play Now / Tester"), then Sign out, confirmed "Yes", took another screenshot (only "Sign in
with Google" and "Sign in with Email / Username"), and tapped only **"Sign in with Email / Username"**. The local
provider returned straight to "Play Now / Tester", Play Now gave LOGGED_IN at 3219,3222 ("Welcome to Old School
RuneScape."). No Jagex login page was ever shown.

## 6. Network QA (docs/app-telemetry.md "Network QA")
Window 22:22:02-22:27:38. Build: this commit's APK, default plugins + devtools (and briefly an R1 test jar, refused).
Sampler: ESTABLISHED uid-10071 rows of /proc/net/tcp{,6} every ~0.6 s, 469 samples.
Run: app start, Sign out, "Sign in with Email / Username", Play Now, LOGGED_IN, walk, `::tele` to Varrock, skills/prayer/
inventory tabs, `::drop` (CONNECTION_LOST then LOGGED_IN), force-stop and launch, "Try again" after ~45 s, Play Now,
`::tele` home, attack a Man, walking for about a minute.
- netd DNS: `api.runelite.net` x2, `static.runelite.net` x2, `sdk.fra-01.braze.eu` x2, `dns.google` x1 (22:23:11,
  right after sign-in: system Private DNS validation, expected).
- App sockets: 10.0.2.2:43594 (game), 10.0.2.2:8080 (sidecar), 104.26.8.233 and 172.67.74.139 :443 (runelite),
  172.64.152.249:443 (Braze), 104.18.32.25:443 (OneTrust), 172.217.114.4:443 (one Google address).
- Must-not-appear: no `*.jagex.com`, `*.runescape.com`, `*.singular.net`, `app-measurement.com` or `*.x.invalid` lookup;
  0 `ConsentSync`/`Singular` log lines; no socket to a Jagex address. **PASS.**

Extra (not part of the release's own traffic): a second sampler ran 22:28:44-22:34:34 with all 87 core and 106 hub
plugins loaded (594 samples). It saw the same expected hosts (also 104.18.35.7 Braze, 172.64.155.231 OneTrust) plus
**185.199.111.133:443 (GitHub, raw.githubusercontent.com range)** from 22:32:46, the moment the host started the hub
batch. No log line names the requester. The documented hub plugins that GET from raw.githubusercontent.com are
C Engineer: Completed (sound list/files, approved gen 26) and Resource packs (only with a pack selected, none was), so
it is most likely C Engineer. It is not a Jagex host and these plugins are not in the APK.

## 7. R1 NetScan refuses a NET-RAW jar
I pushed `hub-qa-netraw.jar` (a copy of the gen 40 inert bait: one uncalled `new java.net.Socket()`, empty plugin
list), with `debug.osrsrl.netallow` empty. The host logged
`W osrsrl-host: netscan: refused hub-qa-netraw.jar: java.net outside NetGuard: [Ljava/net/Socket;] (vet it, or list it in debug.osrsrl.netallow)`
and started the other 4 plugins. The off-device `netscan-check.sh` also reports `REFUSE` (self-test ok). **PASS.**
The jar was removed by name afterwards.

## 8. Plugins (core + all pinned hub plugins)
Run A: all 87 core marker jars + the default 3 + devtools: **91/91 started**. Run B: Run A plus all 106 pinned hub jars
(`host/plugins/hub/plugins.txt` = the 106 dexed jars tested): **197 started, 1 failed to start** (Drop Sounds). The
hub jars were the main tree's `re/host/plugins-extra/hub-*.jar` (built at their pins, 14:22-17:36 today; not part of the
APK). Settings: World Hopper's ping/displayPing/menuOption/quickhopOutOfDanger off (nothing pinged Jagex worlds),
`npcindicators.npcToHighlight=Man`, `currentworld.overlayActivity=true`; the pre-slot settings.properties was restored
byte-identical afterwards.

Errors over both runs (about 9 minutes, logins, combat, bank, tabs), excluding the known FlatDarkLaf info and the
WorldService first-run TimeoutException:
- Drop Sounds: `NoClassDefFoundError: javax/sound/sampled/Clip` while Guice built it; it never started.
- Time Tracking: `NoSuchMethodError: ConfigManager.getRSProfiles()` once per run, in FarmingTracker.checkCompletion (the
  scheduled completion check). The host ConfigManager never had getRSProfiles, so this is not a regression.
Nothing else, in either run.

FPS: 11 fps during login with 91 or 197 plugins, then back to 30 fps once in game (both runs).

Status column: the row's status after this gate. "This run": **V** = feature seen working at this commit (evidence
below), **S** = started with no exception at this commit (feature carried from the generation named in
docs/plugin-host.md), **C** = corrected.

### Core plugins (89 runs, 1 starts)
| Plugin | Status | This run |
|---|---|---|
| Ground Items | runs | V: "Red bead (GE: 1,168 gp) (HA: 2 gp)" (live price) on a spawned bead dropped at 3222,3219 |
| Opponent Information | runs | V: "Man 0/7" box after a kill (both runs) |
| Boosts Information | runs | S |
| XP Drop | runs | V: XP counter + progress bar after combat |
| Status Bars | runs | V: HP/prayer bars beside the side panel |
| Tile Indicators | runs | S |
| Attack Styles | runs | V: "Accurate" from login |
| Chat Filter | runs | S |
| Chat Channel | runs | S |
| Team | runs | S |
| Chat History | runs | S |
| Chat Timestamps | runs | V: "[02:30] [22:30] Welcome to Old School RuneScape." |
| Combat Level | runs | V: "Combat Lvl: 126.1" |
| Cannon | runs | S |
| Daily Task Indicator | runs | S |
| Emojis | runs | S |
| Examine | runs | S |
| Ground Markers | runs | S |
| Implings | runs | S |
| Interact Highlight | runs | S |
| Inventory Tags | runs | S |
| Item Charges | runs | S |
| Item Stats | runs | S |
| Logout Timer | runs | S (logged "idle timeout: 5 -> 5 min (set)") |
| NPC Indicators | runs | V: Men outlined cyan with `npcToHighlight=Man` |
| Object Markers | runs | S |
| Poison | runs | S |
| Prayer | runs | S |
| Random Events | runs | S |
| Regeneration Meter | runs | S |
| Run Energy | runs | S |
| Timers & Buffs | runs | S |
| Time Tracking | runs (completion check throws) | **C**: starts; the scheduled farming completion check throws `NoSuchMethodError: ConfigManager.getRSProfiles()` (host gap), so its completion notifications can't work. Farming tab display carried from 2026-10-16 |
| Woodcutting | runs | S |
| XP Tracker | runs | S |
| Agility | runs | S |
| Chat Notifications | runs | S |
| Fishing | runs | S |
| Cooking | runs | S |
| Idle Notifier | runs | V: "[RuneLite] You are now out of combat!" |
| Mining | runs | S |
| Player Indicators | runs | S |
| Special Attack Counter | runs | S |
| XP Globes | runs | V: globes over the chat tabs after combat |
| Bank | runs | V: "The Bank of Gielinor (39.3M)" |
| Notes | runs | S |
| Slayer | runs | S |
| NPC Aggression Timer | runs | S |
| Mouse Tooltips | runs | S |
| Loot Tracker | runs | S (Loot Logger's man.log was written from its LootReceived) |
| Hiscore | runs | S |
| Skill Calculator | runs | S |
| Menu Entry Swapper | runs | S |
| Clue Scroll | runs | S |
| Item Identification | runs | S |
| Rune Pouch | runs | S |
| Virtual Levels | runs | V: Mining 99/103, "Total level: 2380" |
| Ammo | runs | V: "50" arrow infobox |
| Metronome | runs | S |
| Hunter | runs | S |
| Quest List | runs | S |
| Chat Commands | runs | S |
| Anti Drag | runs | S |
| Fairy Rings | runs | S |
| Puzzle Solver | runs | S |
| Item Prices | runs | S |
| Screen Markers | runs | S |
| Friend Notes | runs | S |
| Barrows Brothers | runs | S |
| Entity Hider | runs | S |
| Herbiboar | runs | S |
| Minimap | runs | S |
| Mouse Highlight | runs | S |
| Smelting | runs | S |
| Bank Tags | runs | V: tag-tab column ("+", tag tab) in the bank |
| Diary Requirements | runs | S |
| Inventory Viewer | runs | V: draws the inventory when another tab is open. Seen once: it kept drawing after the inventory tab was reopened (not investigated, cosmetic) |
| Runecraft | runs | S |
| Boss Timers | runs | S |
| DPS Counter | runs | V: "DPS / tester 14" during a kill |
| Friend List | runs | S |
| Spellbook | runs | S |
| Grand Exchange | runs | S |
| Music | runs | S |
| Instance Map | runs | S |
| World Hopper | starts | S (with ping/hop options off; world list fetch from api.runelite.net only) |
| World Map | runs | S |
| Wiki | runs | V: WIKI banner under the world map orb |
| Key Remapping | runs | S |
| Inventory Grid | runs | S |

### Plugin Hub plugins (77 runs, 29 starts, 1 throws; 2 excluded rows unchanged)
| Plugin | Status | This run |
|---|---|---|
| Quest Helper | runs | S (default plugin; started in every run) |
| Better NPC Highlight | runs | S |
| Monster HP Percentage | runs | S |
| Visual Metronome | runs | V: tick box alternating |
| Attack Timer Metronome | runs | S |
| Customizable XP Drops | runs | S |
| Shortest Path | runs | S |
| Improved Tile Indicators | runs | S |
| Inventory Setups | runs | S |
| Equipment Inspector | runs | S |
| Bank Heatmap | runs | S |
| Wasted Bank Space | runs | S |
| Banked Experience | runs | S |
| Kitten Tracker | runs | S |
| Large Logout | runs | S |
| Radius Markers | runs | S |
| Skills Tab Progress Bars | runs | S |
| Tree Despawn Timer | runs | S |
| Max Hit | runs | V: "Max Hit: 11.0" overlay |
| Wilderness Multi Lines | runs | S |
| Charges Improved | runs | S |
| Bank Tag Layouts | runs | S |
| Spawn Predictor | starts | S |
| Barrows Door Highlighter | runs | S |
| Better Teleport Menu | starts | S |
| Wilderness Player Alarm | runs | S |
| Hunter Rumours | runs | S |
| Ess Pouch | runs | S |
| Plank Sack | runs | S |
| KPH Tracker | runs | S |
| Thrall Helper | runs | S |
| Loot Lookup | runs | S (vetted by R1, loaded) |
| Tile Packs | runs | S |
| Custom Menu Swaps | runs | S |
| Resource packs | starts | S |
| C Engineer: Completed | runs | S (likely source of the raw.githubusercontent.com socket, section 6) |
| Runecrafting Utilities | runs | S |
| Mahogany Homes | runs | S |
| Rogues' Den | runs | S |
| Target True Tile | runs | S |
| Monster Menu HP | runs | S |
| Looting Bag | runs | S |
| Action Progress | runs | V: starts and posts its v1.30 update notice |
| Consumable Cooldowns | runs | S |
| Ground Object Hider | runs | S |
| Annoyance Mute | runs | S |
| Visual Ticks | runs | V: "1"/"2" dots alternating per tick |
| Player Outline | runs | S |
| Bank Memory | runs | S |
| Tree Count | runs | S |
| Not Empty | runs | S |
| Custom Left Click Drop | runs | S |
| NPC ID | runs | V: ids over NPCs (5598 cat, 3108/3106 Man, 2854 rat, 326, 5007 imp) |
| High Alc Profit | runs | S |
| Corner Tile Indicators | runs | S |
| Attack Ranges | runs | V: starts and posts its 1.3 update notice |
| Instant Inventory | runs | S |
| Bank XP Value | runs | S |
| Loot Logger | runs | V: wrote `.runelite/loots/<hash>/npc/man.log` after the kill (removed afterwards) |
| Supplies Tracker | runs | S (wrote its `supplies-tracker/<hash>.txt`, removed afterwards) |
| Damage Counter | runs | S |
| Line Markers | runs | S |
| Random Event Hider | starts | S |
| Coal Bag | starts | S |
| Spirit Tree Map | starts | S |
| Time Tracking Reminder | runs | V: Ready/Dead infoboxes per patch type top-left |
| Emote Clue Items | runs | S |
| Weapon Charges | starts | S |
| Fish Barrel | starts | S |
| Clue Steps | starts | S |
| Pickpocket Helper | runs | S |
| Did I Compost? | starts | S |
| Menu Swapper Extended | runs | S |
| Slayer Assistant | runs | S |
| 1 Tick Flick | runs | V: bar and "Combo: 0" bottom-left |
| AFK Countdown | runs | V: door infobox counting down (4:30, 4:12, 3:59) |
| Ring of Recoil Notifier | starts | S |
| Spam filter | starts | S (wrote its corpora, removed afterwards) |
| Effective Levels | starts | S |
| Path Marker | runs | S |
| Line of Sight | runs | V: grid around the player at 3222,3219 |
| Drop Sounds | **throws** | **C** (was "not run"): fails to start, `NoClassDefFoundError: javax/sound/sampled/Clip` (desktop audio API; Android has none). Needs an AudioPlayer-style shim like C Engineer's, or drop the pin |
| Clue Juggling Timers | starts | S |
| Herbi AFK | starts | S |
| Barrows Potential | starts | V: starts and posts its "has been updated" notice |
| Pyramid Plunder Counter | starts | S |
| Door Kicker | starts | S |
| Gemstone Crab | starts | S |
| Star Info | starts | S |
| Max Hit Calculator | runs | V: "Max Hit: 11" overlay |
| Tick Tracker | runs | V: "100.00%" / "97.56%" top-right |
| Fishing Notifier | starts | S |
| Slayer Task Sorter | starts | S |
| Cannon Highlighter | starts | S |
| Set Timer | starts | S |
| Death Charge Reminder | starts | S |
| Arceuus Timers | starts | S |
| Herblore Recipes | starts | S |
| Multi Lines | excluded | not pinned (unchanged) |
| Monster Stats | runs | S |
| Current World | runs | V: "World 255 / Test server" under the minimap |
| No Examine | starts, no effect | S |
| Decimal Prices | starts (conversion unconfirmed) | S |
| Quick Prayer Preview | runs (host fix) | S |
| Multicolor Highlights | runs | S |
| Pickpocket Guardian | runs | S |
| Unpotted Reminder | runs | S |
| Ground Item Sounds | runs | S (wrote its 5 wavs, removed afterwards) |
| Dude, Where's My Stuff / Companion Pet / Camera Smoothing / Pet Info | excluded | not pinned (unchanged) |

Evidence (session scratchpad `/tmp/claude-1000/-home-alex-osrs-plugins-reverse/a0087aa1-b8d3-439d-add9-f4cbee8fc5e6/scratchpad/`):
`repack.log`, `secretscan.out`, `signin1.png`, `netqa-logcat.txt`, `sockets.txt`, `sockets-plugins.txt`,
`core-start.txt`, `core-run.txt`, `all-start.txt`, `all-run.txt`, `core-login.png`, `core-combat.png`, `core-bank.png`,
`core-combattab.png`, `core-skills.png`, `core-bead.png`, `all-login2.png`, `all-combat.png`, `all-kill.png`, `final3.png`.

## Other observations (not blocking)
- devtools `type_chat`: twice the first `::` command after an action (`::openbank` after login + combat, `::tele` after
  combat) was submitted (varcstr 335 empty) but had no effect; a resend worked. Probably the same family as client-state
  gap 1; no plugin involved.
- The docs/plugin-host.md table should take the two corrections above (Drop Sounds throws; Time Tracking's completion
  check). The dev or orchestrator edits it; a hook limits my edits to records.
- The Network QA record for this run lives here (section 6). The network-qa skill says to add it under a dated heading
  in docs/app-telemetry.md; I can't edit docs.

## Slot
`tools/device-restore-check.sh pre /tmp/osrs-slot/qa` at 22:21; every jar (87 core, 106 hub, devtools, the R1 bait) and
every plugin file and directory created (loots/, plugin-data/ground-item-sounds/*.wav, spam-filter/, supplies-tracker/)
removed by name, one plain `adb shell rm`/`rmdir` call each; settings.properties restored; `debug.osrsrl.devtools 0`;
tester LOGGED_IN at 3219,3222 with only the 3 default plugins. `check` at 22:41: all OK (MANUAL: player at 3219,3222
from the overlay, inventory unchanged apart from the spawned red bead, which was dropped; GE lobster offer not touched).
Lock freed 22:41.

## For the orchestrator (before publishing)
1. **Jagex APK rule**: docs/workflow.md "Client track" step 6 says a GitHub release is published "never with the Jagex
   APK or anything its licence forbids". The release APK is Jagex's app with our additions (inventory in
   qa/client-0.1.0-notes.md). The user's decision to publish it, as relayed, conflicts with that documented rule:
   confirm with the user directly and update the rule, or ship without the APK.
2. **Licence notices**: host.jar inside libosrsrl.so bundles RuneLite client 1.13.1 and runelite-api (BSD-2), Guice,
   Guava, Gson, OkHttp, Okio, commons-lang3/text, javax.inject (Apache-2.0), protobuf-javalite (BSD-3), slf4j (MIT),
   jsr305 (BSD) and aopalliance (public domain); libosrsrl.so links Dear ImGui (MIT). The APK carries none of their
   licence texts. BSD-2/BSD-3/MIT require the notice with binary redistribution, so attach a NOTICE/licences file to the
   release (or embed one in a later build).
3. Secret Protection (the user's suggestion): GitHub secret scanning and push protection cover git content
   (commits, issues, PRs), not release binaries, so section 4's scan is still the check for the APK assets.
