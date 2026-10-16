# Test server evaluation: RSMod, OpenRune and others vs. keeping ours

Written 2026-10-16. Orchestrator correction: saves were already committed and verified (7ee343a) when this was written. Question: should `server/` (our ~4.5k-line Kotlin engine on RSProt 241 plus
the `osrs-241-android` module) be replaced by an existing open-source server? The deciding axis is
**feature coverage**: a port only pays off if the candidate already implements the gameplay we
keep writing by hand. Clones are in `re/rsmod`, `re/openrune`, `re/gielinor`, `re/alter`,
`re/rsprox` (git-ignored). All builds ran in Docker only. "Inferred" marks claims that were not
checked against code or by running it.

Note: the research was cut short by the usage limit. Sections marked **not yet assessed** are open.

## Summary

| | Rev | Lang | Protocol lib | Android module reuse | Persistence | Doors/stairs | Quests/content | License | Activity | Effort |
|---|---|---|---|---|---|---|---|---|---|---|
| **Ours + general systems** | 241 | Kotlin, 4.5k LOC | RSProt 20261001 + our android module | n/a (it is ours) | JSON saves per account in a named volume (`Saves.kt`, 7ee343a, verified live) | one at a time | Cook's Assistant, Sheep Shearer, Romeo & Juliet (hand-scripted) | ours | 38 commits since 2026-10-14 | **~1 week** for data-driven doors/stairs, routefinder, spawns (saves already done) |
| **OpenRune-Server** (RSMod v2 fork) | **241** | Kotlin, ~330k LOC | RSProt **20261001** (same as ours) | likely drop-in (4 DESKTOP call sites); untested | PostgreSQL + Flyway (embedded) | **generic, data-driven** (`loc.toml` tags) | quest framework + 10 quests incl. **Cook's Assistant**; no Sheep Shearer, no R&J | ISC | very active (100 commits in Sep 2026) | **~7-13 agent-days** |
| RSMod (rsmod/rsmod) | 233 | Kotlin, 157k LOC | RSProt 20250909 (osrs-233) | needs RSProt 233->241 port first | SQLite + Flyway | per-object tables | **no quests**, dialogue API only | ISC | **dormant since 2025-10-01**, external PRs never merged | ~2-4 weeks |
| Project Gielinor (Rust) | 239 | Rust, ~75k LOC | own Rust codec | **no** (different language) | SQLite | generic doors | no quests | ISC | 4-day-old repo | dismiss |
| Alter | 228 | Kotlin | RSProt 228 | needs 228->241 | yes | | medium | BSD-2 | stale since 2025-10 | dismiss |

**Recommendation (provisional): do not port RSMod. OpenRune is the only real candidate.** Either
(a) keep our server and lift its general systems (ISC allows copying with the notice): the
`org.rsmod:rsmod-routefinder` library, door/gate/stairs/ladder data and handlers, NPC spawn
tables; or (b) run a time-boxed spike (2-3 agent-days): OpenRune + our patched RSProt with
ANDROID enabled, token login, cache pinned to 2735, logging in from the device. Go for the full
port only if the spike shows the Android client accepting OpenRune's repacked cache and
gameframe. Decision criteria are below.

## Decision criteria

1. **Feature coverage of what Quest Helper needs** (quests, dialogue, doors/stairs, saves,
   skills used by quests). This is the main axis. RSMod fails it (no quests, two real skills).
   OpenRune passes it partly (Cook's Assistant and 9 other quests, data-driven doors, 14 skills).
2. **Revision gap to 241 and protocol plug-in.** Our android module needs RSProt's 241 API
   (`InfoProtocols.alloc(slot, clientType)`, per-client encoder/decoder repositories). OpenRune
   is on the same RSProt build, RSMod is 8 revisions behind.
3. **Client-visible cache.** The Android app must accept whatever cache the server serves over JS5.
   RSMod and OpenRune both repack the cache, so the app would no longer get stock 2735.
4. **Maintenance and fork cost.** RSMod is dormant and single-maintainer. OpenRune churns fast
   (merge cost) and is partly agent-written.
5. **Ops weight.** Ours is one container. OpenRune adds embedded Postgres plus "Central", and it
   refuses to run as root.

---

## RSMod

### 1. Revision and RSProt

- **Revision 233.**
  - `api/core/src/main/kotlin/org/rsmod/api/core/Build.kt:4` sets `MAJOR = 233`, with cache
    OpenRS2 **2293** (`:6-10`).
  - `gradle/libs.versions.toml`: `rsprot = "1.0.0-ALPHA-20250909"` and `net.rsprot:osrs-233-api`
    / `osrs-233-shared`.
  - The boot check `api/net/.../NetworkScript.kt:34` fails when RSProt and RSMod revisions differ.
- **Last bump:** commit ab3aacb8 (2025-09-10, "Bump revision build 231 -> 233") touched 24 files,
  mostly `.data/symbols/*.sym` (+7k lines), plus the clientscript and gameval decoders.
- **RSProt use:** concentrated in `api/net` (47 files) and `api/player-output` (10). It also
  appears in a handful of files in player, registry, game-process, utils-zone and testing.
- **RSProt 233 -> 241 changes that RSMod depends on**, from a diff of
  `server/rsprot/protocol/osrs-233` vs `osrs-241`:
  - The info API was unified. `NetworkService.playerInfoProtocol` / `npcInfoProtocol` /
    `worldEntityInfoProtocol` became `infoProtocols: InfoProtocols`. RSMod uses the old API in
    `RspClient.kt:42-43`, `NetworkScript.kt:51-60` and `RspCycle.kt`.
  - Renamed or versioned messages that RSMod uses:
    - `OpLoc`/`OpNpc`/`OpObj` -> `*V2`
    - `RebuildLogin`/`Normal`/`Region` -> `*V2`
    - `SetMapFlag` -> `V1`/`V2`
    - `UpdateRebootTimer` -> `V1`/`V2`
    - `CamLookAt`/`CamMoveTo` -> `*V3`
    - `IfSetModel` -> `V2`
    - `NpcInfo*V5` -> `V6`
    - About 10 RSMod files are affected.
  - Inferred: 1-2 days of mechanical work. Our `World.kt` already uses the 241 forms as a reference.
- **Cache 2293 -> 2735 (tested).**
  - I pointed RSMod's `downloadCache` at OpenRS2 2735 and ran `packCache` in Docker.
  - It fails with `IOException: Error unrecognised .loc config code: 7`.
  - After a local patch for loc opcodes 6/7 (int model ids, which our `GameData.kt:246-248` also
    handles), it fails on **loc opcode 96**, which our decoder does not know either.
  - RSMod's decoders are strict, and its packer re-encodes types, so every format change in
    rev 234-241 configs needs a decoder and an encoder. The patch was reverted and the 233 cache
    restored.
- **Symbols.**
  - The 45 `.data/symbols/*.sym` files are committed and pinned to 233. 828 type references carry
    identity hashes (`find("cookquest", 6586291)`, `api/config/.../refs/BaseVarps.kt:44`) that are
    verified at boot (`server/app/.../GameServer.kt:214-216`).
  - No sym generator is in the repo. Inferred: they can be regenerated from the 2735 gameval
    archive (RSMod reads it, `api/cache/.../Js5GameValGroup.kt`) or from RuneLite gameval constants.

### 2. Protocol plug-in and a second client type

- **One factory:** `api/net/.../NetworkFactory.kt` (an RSProt `AbstractNetworkServiceFactory`).
- **Desktop is hard-coded in four places:**
  - `NetworkFactory.kt:41-42` (`supportedClientTypes = DESKTOP`)
  - `NetworkScript.kt:59-60` (info alloc with DESKTOP)
  - `PlayerZoneUpdateProcessor.kt:167` (`enclosed[DESKTOP]`)
  - `SharedZoneEnclosedBuffers.kt:20` (keyed per client type, but only DESKTOP is read)
- **How our module plugs in:** at the RSProt service level (`server/rsprot.patch`: `EntityInfoProtocols`,
  `MessageEncoderRepositories`, `MessageDecoderRepositories` register Android when
  `ANDROID in clientTypes`). So a 241 RSMod would get Android codecs by listing ANDROID and
  allocating infos per session client type.
- **Our zone packets** go through our own `AndroidUpdateZonePartialEnclosed` (`World.kt:1551-1557`),
  not RSProt's `ZonePartialEnclosedCacheBuffer`. RSMod's zone processor would need an Android branch.
- **Gameframe:** RSMod picks fixed or resizable only, from `loginBlock.resizable`
  (`content/interfaces/gameframe/.../GameframeScript.kt:69-80`, `GameframeDbTables.kt:44-66`).
  It has no mobile toplevel 601 or enum 1745, although the symbols exist (`interface.sym`
  `601 toplevel_osm`, `enum.sym` `1745 mobile_pane_redirect`).

### 3. Login, auth, networking

- **Password only.** `ConnectionHandler.kt` uses argon2 plus TOTP and auto-registers unknown
  users when `requireRegistration=false`. The dev realm boots with `ignorePasswords=true`.
- **Token login is a stub:** `tokenLogin` logs "Unhandled" and returns `InvalidLoginPacket`
  (`api/net/.../ConnectionHandler.kt`, about lines 178-186).
- **What has to be added or kept:**
  - A token path that maps our LOCAL_AUTH token to a character. Small.
  - Our auth-type-7 decoder and Android CRC decoder. They live in `rsprot.patch`, so they come
    along with our RSProt build.
  - `Http.kt` / `LocalAuth.kt` (jav_config, slr.ws, OpenID, game-session API) are independent of
    the engine and can run unchanged as a sidecar or a module.
- **JS5:** `Js5Store` serves RSMod's packed cache (`.data/cache/js5`), not stock 2735.
  - Inferred: the Android app should accept it, because it downloads everything from our server
    and CRC validation is off. It has not been tested.

### 4. Features (RSMod vs ours)

| Feature | RSMod | Evidence |
|---|---|---|
| Player saves | implemented (SQLite: position, varps JSON, stats, all `InvScope.Perm` invs incl. bank) | `api/db/src/main/resources/db/migration/V1__init.sql:21-84`, `api/account/.../CharacterInventoryPipeline.kt:90` |
| Pathfinding/collision | implemented (BFS routefinder, reach strategies, line of sight; collision from map + loc data) | `engine/routefinder` (= `org.rsmod:rsmod-routefinder:6.0.0` on Maven Central, no deps), `api/cache/.../map/GameMapDecoder.kt:125-209` |
| Doors/gates | partial: per-object tables | `content/generic/generic-locs/.../doors/DoorConfigs.kt:16-27`, `gate/GateConfigs.kt:17-30` |
| Stairs/ladders | partial: spiral stairs and ladders by type (incl. Lumbridge castle `spiralstairsbottom_3`) | `staircase/SpiralStaircaseScript.kt:28-79`, `ladders/LaddersConfig.kt` |
| NPC spawns | implemented: Lumbridge 585 NPC / 57 obj spawns (TOML) | `content/areas/city/lumbridge/src/main/resources/.../npcs.toml` |
| Combat | implemented: melee/ranged/magic, some specials; player item drop on death is TODO | `api/combat/*`, `api/death/PlayerDeath.kt:41` |
| Dialogue | implemented (chatPlayer/chatNpc/choice) | `api/player/.../dialogue/Dialogue.kt` |
| **Quests** | **missing**: no framework, no quest scripts; `cookquest`/`sheep` exist only as varp refs | `api/config/.../refs/BaseVarps.kt:44,54` |
| Skills | woodcutting and elemental spells only; `cooking`/`fishing` are empty stubs | `content/skills/*` |
| Bank, shops | implemented | `content/interfaces/bank`, `api/shops` |
| Mobile gameframe | missing | see 2 |

**Verdict on content:** thin. It has none of the three quests we use, and it lacks spinning,
cooking and shearing.

### 5. Build and run (Docker)

- `docker run eclipse-temurin:21 ./gradlew build -x test`: **BUILD SUCCESSFUL** in 3m09s.
- `./gradlew install` downloads cache 2293, packs it and generates an RSA key: successful in 1m52s.
- `./gradlew run` boots with "Bound to ports: 43594 / Revision: 233 / Supported client types:
  Desktop" and SQLite migrations v1-v6.
- It needs the vanilla cache, xteas, the packed caches (about 720 MB under `.data/cache`) and
  `.data/game.key`.
- The 2735 cache does **not** work as is (see 1).

### 6. License and maintenance

- **License:** ISC (`LICENSE.md`, "Copyright (c) 2025 RS Mod"). Copying code or data only
  requires keeping the notice.
- **Last commit:** fa13b3f6, 2025-10-01. That is more than a year without commits.
- **Commit history:** 1287 of 1297 commits in 2025 are by one author (Tomm0017). Monthly
  commits fell from 366 (2025-01) to 15 (2025-09).
- **External PRs** (#70, #73, #75, #77, #80-82) were all closed without merging.
- The README says "not yet ready for external contributions". In practice that means a fork is
  ours alone: no upstream fixes are coming and nothing can be upstreamed.
- OpenRune (below) is where RSMod v2 development effectively continued.

### 7. Effort (RSMod port)

| Phase | Work | Agent-days |
|---|---|---|
| 1 | RSProt 233 -> 241 API port, using our vendored RSProt build | 1-2 |
| 2 | Cache 2735: loc/obj/npc/etc. decoder and encoder changes; regenerate syms; fix identity hashes and renamed components | 3-5 |
| 3 | Android client type: info alloc, zone branch, mobile toplevel 601 / enum 1745 | 2-4 |
| 4 | Token login + LOCAL_AUTH / Http sidecar, Docker image | 1-2 |
| 5 | Re-write our quests (Cook's Assistant, Sheep Shearer, R&J), spinning, shearing, journal state; device re-verification | 4-7 |

Total: **~2-4 weeks**. Main risks:
- Unknown further cache format breaks.
- The client may reject the repacked cache.
- We carry a 157k-line dormant fork.

---

## OpenRune-Server (shortlist #1)

Repo: github.com/OpenRune/OpenRune-Server, cloned to `re/openrune` (HEAD 9a8b338a8, 2026-10-16).
It is a fork of RSMod v2 (the Gradle group is still `org.rsmod`).

- **Revision 241**, RSProt `1.0.0-ALPHA-20261001`, the same build as ours (`game.example.yml:5`,
  `gradle/libs.versions.toml`). The 241 bump was a 5-line commit (59e8281fb, 2026-10-01), because
  cache decoding lives in OpenRune's own libraries.
- **Protocol:** the same RSMod structure. DESKTOP is hard-coded at:
  - `api/net/.../NetworkFactory.kt:44-45`
  - `NetworkScript.kt:143` (`infoProtocols.alloc(slot, DESKTOP)`, already the 241 API)
  - `PlayerZoneUpdateProcessor.kt:172,185`

  Inferred: swapping in our patched RSProt with dependency substitution and enabling ANDROID is
  1-2 days. It has not been tested.
- **Auth:** the token login is a TODO that returns `InvalidLoginPacket`
  (`api/net/.../ConnectionHandler.kt:191-199`). Password login goes through "OpenRune Central"
  (embedded, with embedded zonky PostgreSQL).
- **Cache:** `:or-cache:freshCache` picks an OpenRS2 cache by revision (241 = 2727/2735/2740;
  inferred: the latest is used) and **repacks the client cache**: CS2 compiled from osrs-dumps,
  interfaces, models, sprites, dbtables (`CacheTools.kt:93-180`). The Android app would get a
  modified cache. There is a risk to mobile-only scripts (inferred).
- **Build/run (Docker, done by the survey agent):**
  - A plain `assemble` fails, because generated code is missing.
  - `freshCache`, then `assemble -x test`, gives BUILD SUCCESSFUL in about 4 minutes.
  - `run` fails as root (Postgres initdb). As a non-root user it boots: "Revision: 241,
    Supported client types: Desktop, ready in 9.3s".

### Feature coverage (checked in `re/openrune`)

| Feature | Status | Evidence |
|---|---|---|
| Quest framework | implemented (QuestScript, journal text/objectives, requirements, rewards) | `content/quest/src/main/kotlin/org/rsmod/content/quest/manager/` (23 files) |
| Quests | 10 implemented:<br>- Cook's Assistant<br>- Restless Ghost<br>- Rune Mysteries<br>- X Marks the Spot<br>- Imp Catcher<br>- Ernest the Chicken<br>- Gertrude's Cat<br>- Daddy's Home<br>- Children of the Sun<br>- a Misthalin Mystery set<br><br>**Sheep Shearer: missing. Romeo & Juliet: missing.** | `content/quest/.../area/lumbridge/CooksAssistant.kt` (338 lines, quest varbits, journal) |
| Doors/gates | **generic, data-driven**: content tags + `next_loc_stage` param per loc, one script per tag | `docs/doors.md`, `.data/raw-cache/server/loc/loc.toml` (299 tagged locs, 39 door entries) |
| Stairs/ladders | generic per loc type (rotation-aware offsets) | `content/generic/generic-locs/.../staircase/StraightStaircaseScript.kt`, `SpiralStaircaseScript.kt:29-30`, `ladders/` |
| Pathfinding/collision | implemented (RSMod routefinder) | `engine/routefinder` |
| Saves | implemented (Postgres, 23 migrations) | survey report |
| NPC spawns/wander | implemented (285 region spawn files; AI timers, e.g. sheep) | `.data/raw-cache/map/npcs`, `content/generic/generic-npcs/.../sheep/Sheep.kt` |
| Shearing | implemented (op1 / shears on sheep, transmog to sheared) | `Sheep.kt:21-40` |
| Spinning | implemented (crafting facility) | `content/skills/crafting/.../scripts/FacilityCraftingScript.kt:37,57` |
| Cooking, milk | implemented (range, cake, dairy cow) | `content/skills/cooking/.../CookingEvents.kt`, `CakeEvents.kt`, `generic-npcs/.../cow/DairyCow.kt` |
| Skills | 14 content modules: cooking, crafting, firemaking, fishing, herblore, magic, mining, prayer, runecrafting, slayer, smithing, thieving, woodcutting, plus utils | `content/skills/` |
| Combat, bosses | melee/ranged/magic, specials, 19 boss modules | `content/bosses/` |
| Bank, shops, ground items | implemented (bank, deposit box, omnishop) | `content/interfaces/` |
| Mobile gameframe 601 | **missing** (fixed, stretch, pre-EoC and display toplevels only) | `GameframeTable.kt` |

**Verdict on content:** substantial. It covers most of the systems we hand-write and one of our
three quests. The missing two quests could be written on its framework in about 1-2 days.

### Effort and risks (survey estimate, inferred)

| Phase | Work | Agent-days |
|---|---|---|
| 1 | Our RSProt build in place of theirs + ANDROID at the four sites | 1-2 |
| 2 | Token login bridged to LOCAL_AUTH, Central stubbed | 1-2 |
| 3 | Pin cache 2735; validate the repacked cache on the Android app | 1-3 |
| 4 | Mobile toplevel 601 row + enum 1745 | 1-2 |
| 5 | Sheep Shearer, R&J, our `::` debug commands; plugin-host re-verification | 2-4 |
| 6 | Non-root Docker image with embedded Postgres | 0.5-1 |

Total: **7-13 agent-days**.

Risks:
- The modified client cache.
- Upstream churn (100+ commits a month). Our android module must follow their RSProt bumps.
- Heavier operations (Postgres, Central, code generation that depends on the cache).
- A 330k-LOC surface, much of it agent-written. AGENTS.md and CLAUDE.md are present, and 81
  commits since July carry Claude co-author lines.

---

## Other candidates

- **Project Gielinor (Rust)**, github.com/autismoyah-hub/project-gielinor-rust-server, ISC.
  - The rune-server.org thread returned **HTTP 403**, so the repo was found by GitHub search.
  - Created 2026-10-12. Pinned to rev **239** (`crates/protocol/src/login.rs:19`).
  - Desktop only (`login.rs:31` rejects client_type != 1). Token logins are rejected
    (`session.rs:170-173`).
  - Its own Rust codecs, so our Kotlin android module cannot be reused. Windows-only `cfg` gates:
    the Linux `cargo build` fails, and only `cargo check --target x86_64-pc-windows-gnu` passes.
  - No quests. Its client is private. **Dismissed.**
- **Alter** (AlterRSPS/Alter): rev 228, stale since 2025-10. Dismissed.
- **kearse/fall-of-varrock** (Alter fork, rev 228, no license): dismissed.
- **Dexploarer/osrs-server** (rev 221, toy): dismissed.
- **runetopic/osrs-server** (rev 211, stale): dismissed.
- **Guthix/OldScape** (rev 189): dismissed.
- **Kronos forks** (~rev 184, no RSProt): dismissed.
- **RSMod v1** (archived): dismissed.
- **Elvarg** (317 protocol): dismissed.
- **Void, 2011Scape, 2009scape**: not OSRS revisions. Dismissed.
- No other public RSProt server at rev 238+ was found.

---

## Keep ours + general systems (the alternative)

What it would take, roughly 1-1.5 weeks of agent work in total:

- **Saves:** `server/src/main/kotlin/rsps/Saves.kt` (JSON per account; position, HP, XP, varps,
  inv/worn/bank) is already being written. About 0.5 day to finish.
- **Pathfinding:** replace our BFS in `Collision.kt` with `org.rsmod:rsmod-routefinder:6.0.0`
  (Maven Central, ISC, no dependencies). It adds reach strategies for walls and locs, NPC sizes and
  line of sight for combat. About 1 day.
- **Doors/gates/stairs/ladders:** copy OpenRune's data-driven approach (content tag +
  `next_loc_stage` per loc, `loc.toml`; straight and spiral stair offset tables) as data. The
  per-loc data and offsets are ISC and keyed by gameval names, which our 241 cache has. About
  1.5-2 days.
- **NPC spawns:** import OpenRune/RSMod spawn TOML for Lumbridge and the quest areas. About 0.5-1 day.
- **Quests:** keep hand-scripting. OpenRune's `CooksAssistant.kt` / `QuestJournal.kt` are a
  reference for journal text and varbits. About 1 day per quest.

Advantages: no protocol or cache risk (we keep stock 2735), one container, and the Android work
stays where it is. Disadvantage: every skill or quest that Quest Helper touches remains our work.

---

## Tooling: RSProx

Repo: github.com/blurite/rsprox, MIT, cloned to `re/rsprox` (HEAD 4f4f8445, 2026-10-14;
"Revision 241" support landed 2026-09-29).

- **Architecture** (`proxy/src/main/kotlin/net/rsprox/proxy/`):
  - It patches RuneLite or the Windows native client to use RSProx's own RSA modulus and its
    loopback world addresses (`127.<world>.<target group>`, `worlds/LocalHostAddress.kt`).
  - `client/ClientLoginHandler.kt` decrypts the login RSA block with its own key and reads the
    ISAAC seeds (`:162-211`). It then re-encrypts the block with the target's modulus
    (`:218-235`) and forwards it.
  - Client types: `Native`, `RuneLite` and `Custom` (`util/ClientType.kt`). There is no Android.
- **Decoders:**
  - Per revision, in RSProx's own modules (`protocol/osrs-241`: 160 server-to-client and 95
    client-to-server decoders, plus prot providers). They are built on RSProt's base libraries
    (buffer, crypto, compression, protocol core), not on RSProt's osrs-241 model.
  - They are selected by **revision only** (`proxy/.../plugin/DecoderLoader.kt:477-499`).
  - An Android decoder set would need a key that includes the client type.
  - Our codecs face the other way: RSProt encoders for server-to-client, decoders for
    client-to-server. RSProx needs **server-to-client decoders**, so the ~140 Android
    server-to-client prots would have to be written as decoders.
  - Inferred: many could be "desktop decoder + our field transform table".
  - The client-to-server decoders, the Android login block (CRC order, auth type 7) and a
    non-loopback bind would also have to be added. BlueStacks reaches the host at 10.0.2.2, not
    at 127.x.
- **Effort for (a), Android-to-test-server transcripts:** inferred at about 4-7 agent-days.
  - Cheaper alternative: our server already logs decoded client packets
    (`LOG_ANDROID_PACKETS`). A server-side transcript of what we send would get most of the
    value for about 1 day.
- **(b) The 241 desktop transcriber as a field-semantics reference:** useful now, reading code
  only (`protocol/osrs-241/.../game/outgoing/decoder/codec/`).
- **(c) The official Android app against Jagex's servers:** not feasible with RSProx (no Android
  client type, no Android patcher). It would need the user's own Jagex login, which agents must
  not automate, and it carries account and ToS risk. Not attempted.
- **Build:** `./gradlew assemble` in `eclipse-temurin:21` **failed in dependency resolution**
  (`:compileJava` compileClasspath). The cause was not investigated; the project uses
  `jvmToolchain(11)`. **Not yet assessed** further.

## Multi-client via RSProx (desktop RuneLite + Android on our server)

Static analysis only; nothing was run against the live server.

1. **Custom target** (README "Private Server Usage", `proxy/.../target/ProxyTargetConfig.kt`).
   RSProx is a real proxy for private targets: it re-encrypts the login with the target modulus
   and forwards to `game_server_port`. Entry in `~/.rsprox/proxy-targets.yaml`:
   - `jav_config_url`: our `Http.kt` `/jav_config.ws`. Its template is desktop-style already
     (`server/protocol/jav_config.ws.template`).
   - `modulus`: from our `/rsa` endpoint (`Http.kt:60`).
   - `revision: 241.1`
   - `game_server_port: 43594` (JS5 shares the port)
   - `runelite_bootstrap_commithash`: a RuneLite bootstrap from the rev-241 window. It must be
     pinned once Jagex moves past 241.
   - Windows/Linux only. RSProx is a Swing GUI, so the user runs it on the Windows desktop.
2. **Login.**
   - Inferred: RuneLite on a custom target uses the classic username/password screen, which
     sends password auth type 0.
   - Our server accepts any authentication type and uses the username (`World.kt:233-349`;
     token logins only fall back to `LOCAL_DISPLAY_NAME`). The patched `GameLoginDecoder` only
     adds type 7 and leaves desktop types as upstream.
   - No server change is expected for login. No Jagex account is involved.
3. **Android-only paths that break a desktop client:**
   - `sendZone` returns early for non-Android players (`World.kt:1551`), so doors, ground items
     and loc changes would be invisible on desktop.
   - `nearby()` filters to Android players only (`World.kt:941`).
   - The Android-only `AndroidUpdateZonePartialEnclosed` / `AndroidZoneProts` would need a
     desktop path through RSProt's `UpdateZonePartialEnclosed`.
   - WORLDENTITY_INFO is skipped for Android but sent for desktop (`World.kt:615-620`), which is
     fine.
   - The gameframe is already per client: toplevel 548/161/601 through redirect enums
     1129/1130/1745 (`GameData.kt:39-47`, `World.kt:335-338`).
   - Inferred: some interfaces opened at mobile-only components may differ. **Not yet assessed**
     in detail.
4. **Value:**
   - Desktop RuneLite with the real Quest Helper on the same server state is a reference to diff
     the plugin host against: highlights, step order, events.
   - Desktop RSProx transcripts of the same server messages come for free, because desktop 241
     decoders exist.
5. **Effort and dependence on the RSMod verdict:**
   - Server side: 1-2 days, mainly the desktop zone path.
   - Setup: about 0.5 day, plus a user-run RSProx on Windows.
   - It does not depend on the port decision. Any RSProt-241 server with DESKTOP enabled works,
     and OpenRune would get it for free.

## Not yet assessed

- OpenRune running with our patched RSProt and an Android login (the spike).
- Whether the Android app accepts a repacked (non-stock) cache.
- The RSProx build failure cause, and a real desktop session through RSProx.
- Full extent of the rev 234-241 cache format changes for RSMod beyond loc opcodes 6/7/96.
