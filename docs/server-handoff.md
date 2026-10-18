# Server work: handoff notes

Status and workflow for whoever continues the test server (`server/`) and the Android protocol
work. The client side (injected library + RuneLite plugin host) is developed in parallel by
another session; see `docs/client-handoff.md`. Protocol findings live in
`docs/android-protocol.md`.

## Migration to OpenRune (2026-10-16, prepared)

The test server is moving to OpenRune-Server (`openrune/`, `docs/openrune-port.md`). Cook's
Assistant, Sheep Shearer and Romeo & Juliet, the test commands and the Android protocol (through
this repo's `rsprot.patch`) all run there.
- **Until the switch:** `server/` (`osrs-rsps-test`, 43594/8080) stays the default and is not
  changed.
- **After the switch:** `osrs-rsps-test` is stopped but kept as a fallback (`docker start
  osrs-rsps-test` after `docker compose down` in `openrune/`), and its `rsps-saves` volume is left
  alone. OpenRune serves 43594, and its sidecar (this server's image, HTTP only) serves
  jav_config/LOCAL_AUTH on 8080.
- **Login key:** the shared key (`server/data/rsa.properties`) keeps existing app builds working.
- **Where work goes:** new content goes into `openrune/src` (exported to `openrune/openrune.patch`).
  Protocol work stays in `server/rsprot.patch`, which both servers build from
  (`openrune/scripts/openrune.sh rsprot`).

## Re-pin after upstream merges

Our tree is `openrune.patch` on top of our fork's main (`REPO`/`COMMIT` in `openrune/scripts/openrune.sh`). When
upstream merges some of our PRs:
1. **Orchestrator:** `tools/fork.sh sync` (fork main = upstream main), and passes the new sha on.
2. **Server dev:** re-pin. In a scratch clone of `openrune/src`: check out the old `COMMIT`, apply
   `openrune.patch`, commit, fetch the fork's main, `git rebase <new sha>`. Resolve conflicts in favour of main
   where main now has the same fix (drop our now-unused helpers and constants). Then in `openrune/src`:
   `git apply -R` the old patch, `git reset`, `git checkout <new sha>`, apply the rebased diff, set `COMMIT` (and
   `REPO`) in `openrune.sh`, `openrune.sh export`. List what was dropped in the port doc.
3. **Server dev:** full clean build (`clean :or-cache:buildCache`, the three fix scripts, `generateRsa
   installDist`), `test` and `integration` (separate runs), boot-check the image as `osrs-openrune:bootcheckNN`,
   commit `openrune.patch` + `openrune.sh` (pathspec), report it ready.
4. **Orchestrator:** deploys when both devices are free (rollback tag first).
First done 2026-10-18 (twenty-second server dev): 9a8b338a8 -> fork c2492318d.

## Latest state (2026-10-18, twenty-second server dev): migration to the fork

**Read `STATE.md` on the fork's `docs` branch first** (it replaces these sections). Details in
`docs/openrune-port.md`, "Twenty-second server dev".

- **Live:** bootcheck23 = fb10fd3 (openrune.patch re-pinned on fork main c2492318d; coal bag wording, ::mapanim,
  HeldU fix). Sidecars run osrs-rsps-test:sd22-members (jav_config param 3 = members world; it is the desktop
  client's members flag).
- **openrune.patch is frozen.** `openrune/src` also holds two unexported changes that now live on branches:
  fix/76 (OBJ_ADD timers) and the Doric/Sheep completeQuest fix (the PR agent adds it to fin/doric and
  fin/sheep-shearer).
- **Migration:** the patch is decomposed into fork branches (local/*, fix/*, content/*, plus the PR agent's fin/*);
  `local/integration-tools` has integration.txt and scripts/build-integration.sh (git rerere resolutions stored).
  `build-integration.sh .` in scratchpad/sd22/or gives integration 99d201e3e with no unrecorded conflicts and no
  duplicate gameval ids. Treasure Trails stays, as local/treasure-trails (ids renumbered, varp names kept).
- **Saved data is keyed by name** (varps, invs, objs, attrs): the upstream id renumbering needs no migration.
- **Not done:** the uncached integration build/tests/boot (the first run failed in buildCache on a varp id
  collision, Pirate's Treasure 64053 vs fin/farming's farming_growth_offset, fixed with 64083), the dry run on a
  copy of the live DB, the cutover. Next steps in STATE.md.

## Latest state (2026-10-18, twenty-first server dev, on OpenRune)

Details in `docs/openrune-port.md`, "Twenty-first server dev". Protocol unchanged.

- **Live:** still ed9cd76. The 2f9253b deploy was refused by the permission check ("Blind Apply") and the
  orchestrator passed the decision to the user. Note: 2f9253b's own outputs carry a **stale item archive** (see
  below), so deploying them changes nothing for items.
- **Bundle ready, boot-checked, not deployed:** a504b83, as image `osrs-openrune:bootcheck21` ("Server ready in
  11.3 s", 07:57 UTC, no exceptions). It was built in a copy of `src` (scratchpad `ctx/src`: buildCache, the three
  cache scripts, installDist) because the orchestrator asked to keep `src/` outputs equal to the 2f9253b
  boot-check. To deploy, either retag that image as `osrs-openrune:241` and `up -d --no-build --no-deps
  openrune`, or rebuild `src/` (`openrune.sh build`) and use the normal deploy line. Both need "OK restart".
- **Done:** `tradeable` second cause = incremental item pack never repacked after the codec fix (the fingerprint
  had no codec version); `PackType.codecVersion` fixes it, upstream 71 complete. Looting bag Settings, Store-X,
  Examine, auto-loot (new `PlayerObjTakeRedirectHook`), bank View panel and deposits; bankside op 1 ("View" /
  "Configure") no longer throws and disconnects (upstream candidate). Count Draynor garlic +1 damage.
- **Check in a client after the deploy:** bank View/deposit of the looting bag, auto-loot in the Wilderness,
  Settings, Store-X, garlic damage. Plus the twentieth dev's list (fletching, looting bag plugin value,
  Doomsayer, `::heal`).
- **Not done:** reconnect-login local upstream patch (wait for the client dev's >60 s Android check, then ask the
  orchestrator; leave `::drop` out); Count returning to the coffin (no source); `LOOTINGBAG_IGNORE_FOOD`.
- **Leftover:** two empty sqlite sidecars `src/.data/cache/incremental_server-{shm,wal}` from a read-only probe;
  my `rm` was refused. They are harmless (`.dockerignore` excludes them).
- **Builds:** `upstream-pr-gradle-*` containers (another agent) run often. Wait for them to finish (one build
  container at a time).

## Latest state (2026-10-18, twentieth server dev, on OpenRune)

Details in `docs/openrune-port.md`, "Twentieth server dev" and the sections after it. Protocol unchanged.

- **Live:** ed9cd76 (06:36 UTC): everything below. Sidecars (8080/8082) run the 78487eb image.
- **Boot-check before every deploy** (the 77b17ed deploy crashed at startup on a wrong `onNpcQueue` overload; the
  server was down ~40 min; fixed in 20fff49): `cd openrune && docker build -t osrs-openrune:bootcheck .`, run it with
  `--rm --tmpfs /app/.data/postgres:uid=1000`, wait for "Server ready". Unit tests never run plugin `startup()` (the
  boot-check also caught a Pickables/banana-tree registration clash). Deploy line: `docker compose --env-file
  switch.env up -d --build --no-deps openrune` (allowed in settings). Builds are capped at 4 CPUs (`OPENRUNE_CPUS`).
- **Building a commit while uncommitted work sits in `src/`:** copy the changed files aside, put back the committed
  versions (a `git clone --shared src` at the pin + the committed `openrune.patch`), build/deploy, then restore.
  `compileKotlin`/tests don't touch `server/app/build/install` or `.data`; `installDist` and `buildCache` do, and the
  image packages both.
- **Done this session:** reconnect login (keeps the session within 60 s; desktop `::drop` and a frozen client
  checked; the >60 s case is the client dev's Android check); Vampyre Slayer and Pirate's Treasure complete with QH on
  the desktop (qhtest9, qhtest10); Count Draynor regeneration/garlic per Mod Ash; Wydin door and Blue Moon stairs
  fixed; 1x XP for new characters' first session; login message from the realm DB (orchestrator set it with
  `tools/openrune-sql.sh`); LOCAL_AUTH account choice (`docker exec osrs-openrune-http sh -c 'echo qhand1 >
  /tmp/local-auth-user'`; checked: qhand1 logged in from Android); fletching (knife on logs, skill Make-X);
  looting bag (Check/Open/Deposit, bag inventory Perm); `::pos`, `::heal`; missing mesanims (`scared`, `sleep`).
- **Not yet checked in a client:** fletching (Action Progress bar), looting bag (plugin value), Doomsayer after the
  mesanim fix, `::heal`. The client dev was told what to check after the 06:36 deploy.
- **Upstream:** 67-71 written (CONTRIBUTING format + Claude Code line; README rows); 72+ candidates: reconnect login
  (after Android confirms >60 s, without `::drop`). Discussion text `batch-2-texts/discussion-script-exception.md`
  (dialogue exceptions disconnect the player by design). Don't edit `PR-BATCH-*.md` or other batch texts.
- **Committed, not deployed (needs "OK restart"):** upstream 71's codec fix in our tree (`stockmarket` follows the
  cache). `tradeable` is still wrong for `tradeable = false` items (second cause in the TOML decode, see the port
  doc); the looting bag keeps checking `stockmarket`. Next step: an or-cache test through the TOML mapper.
- **Do not** suspend client processes for tests (the permission check refused it); DB writes go through the
  orchestrator.
- **Wiki fetches:** `curl "https://oldschool.runescape.wiki/w/<Title>?action=raw"` (no personal data in headers);
  osrs-mcp by hand over stdio (`wiki_page` `maxChars` up to 20000, `cache_search` type `obj` = locs, `item` = objs).
- **Test accounts:** qhtest9 (VS done, 99s), qhtest10 (PT done, employed at Wydin), qhtest11 (Wydin job, apron),
  qhand1 (Android, the client dev's).
- **Next candidates:** apply 71 if wanted; looting bag extras (auto-loot while open, bank emptying, Settings);
  more fletching (stringing, arrows); held plugin-row content (friends chat, GE Sets/±X%, hunter methods).

## Latest state (2026-10-18, nineteenth server dev, on OpenRune)

Details in `docs/openrune-port.md`, "Nineteenth server dev" and the sections after it. Protocol unchanged.

- **osrs-mcp** did not connect to this session; driving `tools/osrs-mcp/run.sh` by hand over stdio works (JSON-RPC
  `initialize`, then `tools/call` for `wiki_page`, `gameval_search`, `cache_search`; a small wrapper script in the
  scratchpad piped the calls and slept ~25 s for the replies).
- **Gertrude's Cat leftovers:** Fluffs has no multinpc in the cache, so the post-kitten hide stays global; now 10
  ticks (guess). Stroke has its own transcript line. Gertrude's stage-2 Shilop exchange (upstream 59, 63).
- **Small follow-ups:** waxwood crates top up to three logs (wiki), the fullscreen map restores the
  stone-arrangement bit on close/logout, `::item` falls back to cache names (`::item saw`) (upstream 60-62).
  Desktop-checked.
- **Item examine** back to upstream's `examine_item` script (type 0); upstream patch 08 withdrawn (orchestrator).
- **Children of the Sun guard tail:** a real desktop run with QH's five hiding tiles was never spotted (vmq1 -> 10);
  standing in the open was. QH's tiles and the server's line-of-sight lookouts agree.
- **New quests (QH coverage):** Doric's Quest, Witch's Potion, Goblin Diplomacy (all three completed with QH on
  fresh desktop accounts qhtest6/7/8), Vampyre Slayer (built and tested, desktop run pending at the time of
  writing; see below). Stage values are QH's step keys; each script checks the cache dbrow endstate at startup.
  Upstream 64-66 (65 uses the drop-table quest gate for the rat's tail; our tree uses a kill hook because
  `assume-completed` makes "during quest" drop gates always false; see the port doc note on that mode).
- **Found on the way:** the Goblin Village lookout ladder had no handler (fixed); the Blue Moon Inn bartender was
  missing (added, sells beer); Morgan's old after-quest lines showed for everyone under assume-completed (moved
  into Vampyre Slayer).
- **Tooling:** `desktop.sh login` with no arguments always logs in desktop/desktop and logs out another account
  first (checks the local player's name). `QuestTalkFixture` has `randomValue` and `interact { }`.
- **Next candidates (ranked):**
  1. Pirate's Treasure (~1 d; research and QH's journal-text dependency in the port doc).
  2. Count Draynor polish: his fast regeneration and return to the coffin; garlic on the first melee hit with a
     30 s duration (wiki).
  3. Upstream patch for Vampyre Slayer + Blue Moon bartender once it has had a desktop run.
  4. Held plugin-row content from the eighteenth dev's list (friends chat, GE Sets/±X%, hunter methods).
- **Test accounts:** qhtest5 (checks), qhtest6 (Doric done, then reset to 0), qhtest7 (Witch's Potion done),
  qhtest8 (Goblin Diplomacy done).

## Latest state (2026-10-17, eighteenth server dev, on OpenRune)

Details in `docs/openrune-port.md`, "Eighteenth server dev" and the sections after it. Protocol unchanged.

- **Misthalin Mystery: complete** with Quest Helper on a fresh desktop account (qhtest2), stage 0 to 135; QH's
  step keys equal the server's `MisthalinStage` values. No server bug found in the quest.
- **Ernest stage 2 confirmed** on a fresh account (qhtest3): first Oddenstein talk sets varp 32 = 2.
- **Black screen on login (desktop) fixed** (upstream 57): the login fallback could pick the fullscreen world map
  frame (`toplevel_display`) and save it. Reproduced with the stone-arrangement bit (set by opening the fullscreen
  map) plus a saved frame that does not match the client mode. Android is not affected.
- **Children of the Sun** sets `vmq1` = 14 (QH "Report back to Sergeant Tobyn") on the fourth correct mark
  (upstream 58; the trigger point is a guess). His Faithful Servants and Daddy's Home stages match QH (audit in the
  port doc).
- **Desktop driving:** fresh accounts via `desktop.sh login <user> <pass>`; scene-base / `invoke_menu_action` tips
  in docs/multi-client.md "Driving tips". QH drops the helper at the first region load after a new account's
  first login (QH behaviour); teleport once before "Start Quest Helper".
- **Deployed** 23:57 UTC: 43e944f + 330dc44.
- **Test accounts:** qhtest1 (Misthalin stage 25), qhtest2 (Misthalin done),
  qhtest3 (Ernest stage 2, fullscreen-map repro), qhtest4 (Children of the Sun, Daddy's Home, HFS done; 99 stats from `::master`). qhtest1/qhtest3 recovered after the 57 deploy (log in to 164).
- Upstream: 58 patches; all `git am` on the pin (scratch clone `up` in this session's scratchpad has 01-58).
- **Children of the Sun: complete** with QH on fresh qhtest4 (deployed 330dc44): the 4th correct mark sets 14 and QH
  shows "Report back to Sergeant Tobyn"; Tobyn -> roof 16 -> 24, scroll 1 QP. The guard tail (stage 6) was skipped
  with `::quest children 10` (real-time line-of-sight stealth; unit-tested).
- **Daddy's Home and His Faithful Servants: complete** with QH on qhtest4 (details in the port doc).
- **Next candidates (ranked, eighteenth dev):** all 13 OpenRune quests/miniquests have now had a QH run (Cook's
  Assistant, Sheep Shearer, Romeo & Juliet on Android in phase 3.3; the other 10 on the desktop).
  1. Gertrude's Cat leftovers (~1 h): returning the kitten hides the shared Fluffs npc for 100 ticks for everyone
     (`Fluffs.returnKitten`), and Stroke runs the pick-up handler. First check whether the cache's Fluffs is a
     multinpc on the quest varbit (osrs-mcp `cache_search`); if so the global hide can go.
  2. Small follow-ups (<1 h each): opening the fullscreen map overwrites the player's stone-arrangement choice
     (`changeGameframe` copies the fullscreen frame's flag; after 57 a mismatched login then picks pre-EoC instead of
     classic); the Daddy's Home waxwood crate gives 3 logs per search with no cap (**unverified** against OSRS; check
     the wiki/transcript before capping); `::item saw` fails on a missing `obj.saw` mapping (debug only).
  3. Children of the Sun guard tail: a real run (QH's puzzle wrapper lists its hiding tiles; compare them with the
     server's line-of-sight lookouts in `CotsRoute`). Unit-tested, never run in game.
  4. Held content behind plugin rows: Music area sounds; the remaining hunter methods for Hunter Rumours
     (deadfall, net trap, falconry, pitfalls, tracking); Spawn Predictor; GE Sets and custom +/-X% buttons; real
     friends chat (~1-1.5 d) instead of `::fakeclan`; FRIENDLIST_DEL (low).
  5. New quests beyond the 13 (new content, larger).

## Latest state (2026-10-17, seventeenth server dev, on OpenRune)

Details in `docs/openrune-port.md`, "Seventeenth server dev" and the sections after it. Protocol unchanged.

- **Deploy with `openrune/scripts/openrune.sh deploy`** (`docker compose --env-file switch.env up -d
  --build`). A bare `docker compose up` comes up on 40255/8081 and the app cannot connect.
- **`::fakeclan`** (test command, `content/other/commands/ChatChannelTestCommands.kt`): a fake friends chat
  ("Test FC") and clan channel ("Test Clan") pushed to the player's own client, plus `add|del <name>`,
  `say <name> <text>`, `guest`, `leave`. Device-checked: the chat-channel and "Your Clan" tabs, add/del
  (single-user update and CLANCHANNEL_DELTA), and chat lines in both. The clan tab needs CLANSETTINGS_FULL
  and varbit `clan_affinitystatus` = 3; login now sets it (and the guest one) to 1 ("No current clan").
  The client dev verified its readers and Chat Channel with it. Args arrive lowercased, one word per name.
  Real friends chat would cost ~1-1.5 d for one world (2 d with Setup); real clans several days.
- **Friend List** device-checked with `tools/multiclient/desktop_login.py --user desktop` (a friend):
  "desktop has logged in." / "has logged out." and the world in the friends tab.
- **Diary B** device-checked: Lumbridge Teleport spell and an Al Kharid iron ore (rock at 3295,3311)
  -> Lumbridge & Draynor 3/41, persists over a relog.
- **GE partial fills + buy limits** (upstream 50): fills in up to 4 parts with "Grand Exchange: Bought n /
  total x Item." lines (wording a guess); buy limits from the wiki (`enum.ge_buy_limits`, CC BY-NC-SA 3.0,
  attributed in the file header); over-limit offers wait for the 4-hour window. Device-checked (lobsters
  3/6/9/10, bronze pickaxes stop at 40/50).
- **GE trade history** (upstream 51): clerk History / GE History button -> ge_history 383 with the last 40
  trades; Buy-offer on a line. Device-checked 14:57 (620ac7f is live).
- **Desktop client:** an isolated desktop RuneLite ("desktop") runs through RSProx (docs/multi-client.md);
  its own lock is `LOCK_NAME=desktop tools/device-lock.sh take|free server`; after a container restart run
  `tools/multiclient/desktop.sh relogin`. Do not use `desktop_login.py --user desktop` while it is online.
- **GE tax-exempt list** (upstream 52, wiki, CC BY-NC-SA 3.0 attributed) and **20 more diary task hooks**
  (upstream 53; list and area boxes in the port doc). Desktop + Android sample checks passed (534b5dd live).
- **Desktop samples:** `tools/multiclient/desktop-devtools-mcp.sh --tool <name> '<json>'` (get_var varbit,
  interact_npc with npcIndex, list_npcs, list_ground_items to confirm a death). The desktop account has low
  combat stats; `::god`/`::maxhit` toggles (turn both off after). If the client process is gone,
  `desktop.sh stop`, `start`, then `login`.
- **Quest Helper / quests (desktop):** The Restless Ghost, Imp Catcher, Rune Mysteries, Ernest the Chicken
  complete server-side (varp trails in the port doc). Fixed after the runs: Ernest stage 2 (upstream 54), the
  Lumbridge/Edgeville general store stock (55), Misthalin Mystery Mandy guards (56) - built, not yet checked in
  game. Audit of X Marks the Spot / Gertrude's Cat / Misthalin Mystery in the port doc. Desktop: QH must come
  from the Plugin Hub (a sideloaded QH jar breaks ObjectSteps; docs/multi-client.md). Quest driving tips: a
  dialogue driver over devtools `get_dialogue`/`select_option`/`continue_dialogue`; `interact_object` by
  `objectId` for multilocs (name filter fails); `pickup_item` needs `itemId` + `x`/`y`; the desktop account
  is weak (10 HP), so avoid aggressive monsters or use `::god` (turn it off).
- **More quests (desktop, 18:00-18:12):** X Marks the Spot and Gertrude's Cat complete; Lumbridge store spade
  checked; Misthalin Mystery Mandy guards checked (full playthrough not done). 90247ad widens the Ernest stage-2
  fix (any Oddenstein talk once Ernest is found) - **built, NOT deployed** (needs a restart).
- **Next candidates:** deploy 90247ad and check Ernest stage 2 on a fresh account (`::quest ernest reset` keeps the
  quest's internal flags, so use an account that never did it); a full Misthalin Mystery run; Gertrude's Cat
  open gaps (shared Fluffs hidden for 100 ticks for everyone, Stroke = Pick-up); remaining quests (Children of
  the Sun, Daddy's Home, His Faithful Servants) for Quest Helper.
- Upstream: 56 patches; all `git am` on the pin (scratch clone `pin` in this session's scratchpad, branch
  `up` with 01-50).
- **Deployed** 18:00: 84030f3. Committed, not deployed: 90247ad (Ernest widening; image prebuilt). Tester LOGGED_IN at Lumbridge, Lumbridge & Draynor 4/41. Tester LOGGED_IN at Lumbridge on the
  normal build, no channel joined; ~100k coins, 40 noted bronze pickaxes, a small pouch, a rune
  pickaxe, an iron ore; the 1 gp lobster buy still open in GE slot 1.
- **Next candidates** (orchestrator to pick): diary task hooks (list sent), GE tax-exempt list, Sets, custom ±X%;
  real friends chat if the fake one is not enough; Better Teleport Menu item families. Held: Music area
  sounds, remaining hunter methods, Spawn Predictor. Low: FRIENDLIST_DEL.

## Latest state (2026-10-17, sixteenth server dev, on OpenRune)

Details in `docs/openrune-port.md`, "Sixteenth server dev" and the sections after it.

- **Grand Exchange** (`content/interfaces/grand-exchange`, upstream 48): device-checked end to end with
  the core GE plugin (`EXTRA=grandexchange`). Clerks' Exchange (also across the booth), buy via the
  mobile item search, sell from the inventory, quantity/price buttons, Enter quantity/price, Confirm,
  Abort, Collect, Collect-all, the banker's Collection Box, relog persistence. Fill rule: the server is
  the only counterparty at the guide price (store cost); at-or-better offers fill 2 ticks after Confirm,
  others stay open until aborted; 2% tax; no partial fills/buy limits/history/sets.
- **Two bugs found and fixed on the way:** OpenRune's CS2 compiler emits opcodes 1/2 for long varps
  (Jagex: 64/65), patched after each cache build by `openrune/scripts/fix_long_varps.py` (issue note
  `openrune/upstream/ISSUE-long-varp-opcodes.md`); RSProt's `UpdateStockMarketSlotV2Encoder` Reset
  wrote slot 0 (fixed in `server/rsprot.patch`, note `ISSUE-rsprot-stockmarket-reset-slot.md`).
  VARP_LONG and UPDATE_STOCKMARKET_SLOT_V2 are now live-verified on Android.
- **Diary journals** for all 12 areas (wiki task lists, plugin wording where it has one).
- **Diary task progress** (upstream 49): 11 Lumbridge & Draynor / Varrock tasks drive the client's
  count varbits and strike lines in the journal; no chat message (wording unknown). Device-checked
  with Hans (tab 1/41, struck line) and the new journals (all 12 tab totals match).
- **Friends chat / clan:** not in OpenRune; Android encoders are bound, so a `::fakeclan`-style test
  command pushing UPDATE_FRIENDCHAT_CHANNEL_FULL_V2 / CLANCHANNEL_FULL is the cheap route if the client
  dev needs one (~1 h, not started).
- Upstream: 48 GE, 49 diary areas + progress; all 49 `git am` on the pin (scratch clone `up` in this
  session's scratchpad has 01-49 committed).
- **Deployed** 13:10: everything up to efbe8df. Tester LOGGED_IN at Lumbridge (3221,3218) on the
  `EXTRA=achievementdiary` build; ~99.6k coins, 10 noted lobsters, law/air/earth runes; an open 1 gp
  lobster buy in GE slot 1; Lumbridge diary easy 5 (Hans) done.
- **Next candidates:** more diary task hooks (the rest of Lumbridge & Draynor / Varrock easy where content
  exists), GE extras (History via `ge_history_addline`, clerk Talk-to, Sets, custom ±X% buttons),
  Flipping Utilities if the orchestrator wants it. Held: Music area sounds, deadfall/net
  trap/falconry/pits/tracking, Spawn Predictor. Low priority: FRIENDLIST_DEL.

## Latest state (2026-10-17, fifteenth server dev, on OpenRune)

Details in `docs/openrune-port.md`, "Fifteenth server dev". Protocol unchanged.

- **Deployed** 14:56 UTC: everything up to 0631589 (a0e18ab jewellery charge slot + dog shelter
  crash, 0631589 achievement diary journals). Tester LOGGED_IN at Lumbridge on the normal build,
  wearing a games necklace(7); the old inventory (necklaces, loot sack, clue items, butterfly jar,
  jerboa tail, net, box trap) is in the bank; `::god`/`::maxhit`/`::freeze` are off.
- **187 menu tap** was the host (BTM's empty Java onOp listener swallowed the tap), fixed by the
  client dev (1c7cee8). No "1:" labels on mobile is stock (script 217 turns hotkeys off). Chase's two
  187 menus and the jewellery Rub menu work on the device.
- **Fixed + device-checked:** jewellery charges keep their slot (a worn necklace moved to the head
  slot); dog shelter Adopt crash (`varbit.dog_unlock_0`; bit 0 = the default breeds).
- **Device-checked:** worn games necklace destination ops, butterfly catch, jerboa in a box trap.
- **Plugin evidence:** DPS Counter runs (Mole); Runecraft draws rift outlines (inner Abyss); Boss
  Timers shows nothing after a Mole kill: a client gap (`NPC.isDead()` for NpcUtil.isDying).
- **Achievement diary journals** (Diary Requirements row): Lumbridge & Draynor and Varrock open
  from the Achievement Diaries tab; the plugin annotates the task lines (device-checked). Other areas
  and task progress are not done.
- **Instances for the client dev:** GWD Bandos door (`loc.godwars_dungeon_door_bandos`, op1 public
  room, op2 private) after `::varbit godwars_counter_bandos 40`; `::tele 2851 5333 2` is a guess for
  the tile outside it.
- `tools/repack.sh` `OPENRUNE=1` defaults now match the live container (8080 / 43594).
- **Upstream:** 45 charge slot, 46 default dog breeds, 47 diary journals; all 47 `git am` on the pin
  (scratch clone `up` in the shared scratchpad has 01-47 committed).
- **Next (ranked with the orchestrator):** more diary areas or task progress if wanted; then the
  Grand Exchange (minimal ~2-day version: interface 465, instant buy/sell at guide price,
  UPDATE_GE_OFFERS slot states), held until approved. Held: Music area sounds, deadfall/net
  trap/falconry/pits/tracking, Spawn Predictor. Low priority: FRIENDLIST_DEL.

## Latest state (2026-10-17, fourteenth server dev, on OpenRune)

Details in `docs/openrune-port.md`, "Fourteenth server dev". Protocol unchanged.

- **Deployed** 13:37 UTC: everything up to 5e0e648 (Rumours part 1, butterfly netting, guild
  dialogues + jerboas, the varps-before-first-tick fix, jewellery teleports with Rub on op 4).
  Tester LOGGED_IN at Lumbridge on the normal build; Hunter 14M XP; Rumours unlocked, one completed,
  current rumour Gilman's red chinchompa; Ahrim killed in the current Barrows run; inventory full
  (a basic loot sack and a games necklace(8) replaced two copper ores).
- **Device-checked:** Hunters' Rumours dialogues with the hub plugin (assign, remind, hand-in, "red
  chinchompa"); Kitten Tracker after relogs (varp fix); Barrows kill varbits with the Barrows plugin.
  Not device-checked: butterfly catches, jerboas, rumour switch lines, Verity's back-to-back toggle,
  worn jewellery destination ops.
- **Varps set before the first processed tick** (`Player.varpsSetBeforeFirstTick`, flushed in
  `PlayerMainProcess`): generic fix for varps the setters dropped between the login sync and the
  first tick (follower_npc was one).
- **Jewellery teleports** (`content/travel/jewellery-teleports`): games necklace, dueling, glory,
  skills necklace, combat bracelet. **Open:** on Android the Rub menu (interface 187, script 217,
  `ProtectedAccess.menu`) shows but a tap selects nothing. The entries are `cc_create` text
  components with no ops (osrs-dumps `[proc,menu_createentry]`; on mobile script 217 forces
  hotkeys off), selected through `ifSetEvents(menu:lj_layer1, 0..127, PauseButton)`. Next: a device
  slot with `LOG_ANDROID_PACKETS=true` to see what a tap sends (nothing, an IF_BUTTON, or a
  RESUME_PAUSEBUTTON the server drops), and compare with the working chatmenu options. If the
  mobile client never resumes 187, consider the chatbox `choice` menu for mobile players
  (`Player.mobileClient`) as a stopgap. Affects every `menu()` user (dog shelter Chase too).
- **Device tips:** Burrow npcs wander; `::npc 500 hg_<name>` spawns one on the player. `::item` with
  a full inventory gives "x 0". `server/tools/device.sh login` can hit "Sign out" when the screen is
  not the one it expects (answer No). Barrows: the Strange Old Man blocks digging until his quest;
  `::tele 3557 9700 3` lands in Ahrim's crypt.
- **Upstream:** 41 butterfly netting, 42 guild dialogues, 43 varps before the first tick, 44
  jewellery teleports; all 44 `git am` on the pin (scratch clone recipe as before).
- **Next (ranked with the orchestrator):** the jewellery menu tap on Android (above); then
  orchestrator's choice. Held: deadfall/net trap/falconry/pits/tracking (Rumours), Spawn Predictor.
  Low priority: FRIENDLIST_DEL.

## Latest state (2026-10-17, thirteenth server dev, on OpenRune)

Details in `docs/openrune-port.md`, "Thirteenth server dev". Protocol unchanged.

- **Deployed** 12:14 UTC: everything up to 14127aa (640abbd, Rumours part 1, is not deployed) (424fc37 Varrock platebody smelting, 11ac6d7
  social resend off by default, e8cc07c + 14127aa Herbiboar). Tester LOGGED_IN at Lumbridge on the
  normal build with the kitten; wears Varrock armour 1; Hunter and Herblore 14M XP; carries 3 copper
  ore, a bronze bar, a chinchompa and an unidentified fossil.
- **Varrock platebody** (`SmeltingScript`): the extra bar smelts the next ore set (its own ores and
  XP) with the Smelting plugin's spam line "The Varrock platebody enabled you to smelt your next ore
  simultaneously." ("simultaneously." and the Make-X handling are guesses). Tier caps already in
  `SmithingBonuses` (steel/mithril/adamant/any by armour 1-4, Edgeville only). The extra bar itself
  was not seen on the device (10%); a bronze bar and the plugin's "Bars: 1" were.
- **Box traps device-checked** (chinchompa caught, Check, plugin trap tracking).
- **Herbiboar** (`HerbiboarScript`, pack `HerbiboarTables`, dbtables 60013-60016, varps 63890-63891):
  device-checked end to end with the core Herbiboar plugin (start, 3 clue spots, tunnel 8, harvest).
  The network was dumped from the map with a throwaway test (`MapLocListDecoder` over map squares
  56-59 x 58-61; a scratch test in a module that has fastutil on its test classpath, e.g. draynor's
  `src/test`; remove it before `export`). Trail values: dir = hidden next leg, dir+2 revealed,
  dir+4 faded (confirmed by the plugin on the device).
- **Client-side gap (relayed to the client dev):** `AndroidClient.getMapRegions()` is empty while
  LOADING, so region checks done in `GameStateChanged(LOADING)` (Herbiboar plugin) fail after a
  teleport/login. Device workaround: `adb shell "setprop debug.osrsrl.probe 'HerbiboarPlugin.startUp()'"`
  then clear it. The probe (`debug.osrsrl.probe <Plugin.field|method()>`) is handy for reading any
  plugin's state.
- **Upstream:** 38 (Varrock platebody, needs 09), 39 (Herbiboar, needs 15/20/35); all 39 `git am`
  on the pin (the scratch clone with 01-39 applied makes the next patch easy: copy files, commit,
  `format-patch -1`).
- **`server/tools/device.sh login`** now checks the OpenRune log (`GAME_CONTAINER` overrides).
- **Hunters' Rumours part 1** (640abbd, built and boot-checked, **not deployed**): state in server
  varps, assignment (catchable creatures only: wagtail, grey/carnivorous chinchompa, herbiboar),
  rare-part drops with the wiki rates and pity on trap Check / herbiboar harvest, turn-in XP + loot
  sack (`HunterRumours.turnIn`), quetzal whistle Rumour op, `::rumour [hunter] [creature]` /
  `::rumour turnin [hunter]` / `::rumour`. Integration tests in `HunterRumoursTest`. Upstream 40.
- **Next:**
  1. Deploy 640abbd ("OK restart") and device-check with `EXTRA="hunter hub-hunter-rumours"`:
     `::rumour cervus grey_chinchompa`, whistle Rumour op (needs a whistle: `::item
     hg_quetzalwhistle_basic`), box-trap chinchompas until "You find a rare piece...", the plugin's
     infobox/catch count, `::rumour turnin`.
  2. Guild hunter dialogues (Gilman, Cervus, Ornus, Aco, Teco, Wolf, Verity) from the wiki
     transcripts (`Transcript:Huntmaster_Gilman_(Novice)` etc.), calling `assign`/`turnIn`. Blocked
     until the client dev confirms the Android host emits DIALOG ChatMessages (the plugin parses
     "<Hunter name>|..." lines inside the Burrow 1549-1565, 9449-9464).
  3. Cheap wins to offer the orchestrator first: a butterfly net (catch butterflies/moths with a
     net, ~0.5 d) would unlock 5 more rumours; deadfall/net traps more.
  4. FRIENDLIST_DEL on Android (low priority, see the twelfth dev's notes).

## Latest state (2026-10-17, twelfth server dev, on OpenRune)

Details in `docs/openrune-port.md`, "Twelfth server dev".

- **Deployed** 11:13 UTC: everything up to 5ff197d (a114f17 trace/social commands, 4fc1f1d Hunter
  traps, 5ff197d Giant Mole KC + kitten growth). 11ac6d7 (social resend off by default) is committed,
  not deployed; the live container has the resend on (`::socialresend off|on` switches it at runtime).
  The tester is LOGGED_IN at Lumbridge on the normal build, "desktop" is a friend again.
- **Friends list:** the outgoing trace (`OUT #n`, now off unless `RSPROT_OUT_TRACE=n` or
  `::traceout n`) and the client dev's friend probe (`setprop debug.osrsrl.friendprobe 1`) show the
  login UPDATE_FRIENDLIST landing (state 1 -> 2) with one friend and with an empty list, with and
  without the resend; "Loading friends list" did not reproduce. Resend kept as a diagnostic, off by
  default from 11ac6d7. `::social [both|loaded|update]` sends the pieces by hand. 707's sub-tab order
  (chat-channel / your clan / other clan / grouping) is device-checked.
- **Open (low priority): FRIENDLIST_DEL** reaches the server and Central deletes, but the Android
  list keeps the entry until relog (see the port doc for the lead).
- **Hunter traps** (`HunterTrapScript`, dbtables 60011/60012, timer 470): bird snares and box traps
  for the core Hunter plugin. Device-checked: lay, catch, Check with a spawned crimson swift
  (`::item hunting_ojibway_bird_snare 3`, `::npc 500 hunting_bird_jungle`). Box traps (Hunter 27+,
  `::npc 500 hunting_chinchompa` etc.) not tried on the device. The plugin jar is
  `EXTRA="hunter" tools/plugin-test.sh`.
- **Giant Mole kill count** (KPH Tracker): `::tele 1759 5189` (mole lair, its spawn) or
  `::npc 500 mole_giant`; `::maxhit` speeds the kill; message "Your Giant Mole kill count is: N".
  Not device-checked. Scurrius already had a KC.
- **Kittens** (Kitten Tracker): `::item kittenobject` and Drop it (or ask Gertrude, 3151,3410, after
  Gertrude's Cat); growth event every 90 s says "Meow!", growth paused in modals, "Your kitten got
  lonely and ran off.". Not device-checked.
- **Upstream:** 35 hunter traps, 36 Giant Mole KC, 37 kitten growth; all 37 `git am` on the pin.
- **Device tips:** spaces in `adb input text` must be `%s`; a typed `::` line stays unsent unless the
  keyboard is shown (tap 90,220 until `mInputShown=true`, then keyevent 66); every app restart costs
  ~45 s of "account still logged in" (Try again at 958,668). Del Friend at 1577,1013, Add Friend
  1400,1013, friends tab 1840,852, side_channels 1843,768 (sub-tabs at y 520: 1340/1440/1537/1637).
- **Next candidates (sized 2026-10-17, sent to the orchestrator, not started):**
  1. Barrows Brothers + Smelting rows: OpenRune already has both. Barrows (`content/bosses/barrows`)
     sets `barrows_killed_*` (the plugin reads varbits 457-463), opens puzzle 25 and reward 155, and
     sends "Your Barrows chest count is:". Smelting sends "You retrieve a bar of ..." and "You remove
     the cannonballs from the mould." (spam), which the plugin matches; only the Varrock-platebody
     line differs ("Your Varrock armour helps you smelt an extra ..." vs the plugin's "The Varrock
     platebody enabled you to smelt your next ore"). Device check only (~15 min each), plus the box
     traps.
  2. Herbiboar (~1-1.5 days): nothing in OpenRune. Fossil Island start rocks/mushrooms, trail
     varbits and locs the plugin reads (ids 3681-3808, 30519-30523, 14651/14652/14907/14908 in
     HerbiboarPlugin), the herbiboar npc, harvest (herbs, Hunter/Herblore XP).
  3. Hunter Rumours (Hunter Guild rumour tasks; easier now that traps exist, ~1-2 days); Spawn
     Predictor needs the Fight Caves (not in OpenRune; multi-day).
  4. FRIENDLIST_DEL on Android (low priority, see above).

## Latest state (2026-10-17, eleventh server dev, on OpenRune)

Protocol unchanged. Details in `docs/openrune-port.md`, "Eleventh server dev".

- **Deployed** at 08:55 UTC: everything up to 245ed50 (stage 3 committed right after the deploy;
  same build). 20d266c (social fixes) is built and boot-checked but not deployed. The tester has the hard casket rewards, a rune pouch (100 air) and a hard clue in
  the inventory; the plank sack holds 2 planks + 3 oak.
- **Rune Pouch** (`content/skills/magic/rune-pouch`): interface 190 with Store/Withdraw
  quantities, drag, Empty, rune-on-pouch, on the `rune_pouch_type/quantity` varbits.
  Device-checked with the RuneLite Rune Pouch overlay. **Plank Sack** (`content/other/plank-sack`):
  Fill/Check/Empty on `plank_sack_*`, storage hook; device-checked with the hub Plank Sack overlay.
- **Hard clues** (treasure-trails engine, `TrailTier.Hard`): the core tier, the hard casket, the
  7 hard maps (identified by rendering; see the port doc), puzzle boxes (slide puzzle 306, inv
  140, server-mirrored moves), coordinate wizards and the Double agent. Device-checked: Yanille
  map, dig, casket; puzzle box with RuneLite's Puzzle Solver. Not device-checked: wizards,
  Double agent, challenge talks, emotes. `::clue hard [row] [left]`, `::clue solve` (solves the held
  puzzle box).
- **Smaller fixes:** fairy ring search input closed with `meslayer_close(28)` on teleport (dialog
  abort never covers mode 28; not device-checked); `::setxp`/`::addxp` up to 200M; friend add
  answers with the new friend's UPDATE_FRIENDLIST entry (Central pushed one only for online friends).
- **Open: mobile friends list stuck "Loading friends list"** (root cause unknown). The client dev's
  dump: the two-faces tab is `side_channels` 707 (clock 707:8, empty contents), and 429 is loaded
  but friend_count < 0. Android dispatcher: FRIENDLIST_LOADED (69) sets friend-manager state 1,
  UPDATE_FRIENDLIST (25) sets 2 even for an empty list (re/decomp FUN_004fbd30). The server sends
  both (desktop order via `desktop_login.py` DEBUG=1: FRIENDLIST_LOADED 3rd, UPDATE_FRIENDLIST
  ~120 packets later after IF_OPENTOP), so the Android UPDATE_FRIENDLIST apparently does not land.
  **20d266c is committed but NOT deployed**: a mobile-only resend of the social snapshot after
  `loadDelay()` + 2 ticks (tagged unverified, a stopgap) and `SideChannelsScript` (707 sub-tabs open
  7/701/702/76 into `side_channels:contents`, tab order a guess). The orchestrator wants the root
  cause: next deploy should add a one-off outgoing log of Android sends around login (opcode, size,
  first bytes; e.g. in `AndroidDelegatingEncoder` or the game channel's outbound handler, gated
  like `IncomingPacketTrace`) to see whether the first UPDATE_FRIENDLIST went out and its bytes
  (empty-list encoding, size, ordering). Not coded yet. Then device-check: 429 shows "You may add
  friends...", Add Friend for the headless `desktop_login.py --seconds 300` account shows it online
  (world 255), and the 707 sub-tabs open their panels.
- **Next plan (agreed with the orchestrator, plugin-backed first):**
  1. **Hunter traps** for RuneLite's Hunter plugin (~1 day): it tracks trap locs only
     (`HunterPlugin.onGameObjectSpawned`): placed = `loc.hunting_ojibway_trap` 9345 / `hunting_boxtrap_empty`
     9380 on the tile the player stood on the previous tick; transition = `*_trapping_*`; full =
     `*_full_*`; empty/failed = `hunting_ojibway_trap_broken` 9344 / `hunting_boxtrap_failed` 9385.
     Spawns for `npc.hunting_bird_{jungle,desert,woodland,polar,coloured?}`, `hunting_chinchompa(_big,_black)`,
     `hunting_ferret` exist in `.data/raw-cache/map/npcs`. Items: `obj.hunting_ojibway_bird_snare`,
     `obj.hunting_box_trap`, `obj.chinchompa_captured`, `obj.chinchompa_big_captured`,
     `obj.chinchompa_black`, `obj.hunting_ferret`, `obj.spit_raw_bird_meat`, feathers
     `obj.hunting_{jungle,desert,woodland,polar,stripy_bird}_feather`. Plan: Lay (loc on the tile,
     player steps off), catch roll per tick with a matching creature nearby (creature despawns),
     trapping -> full / failing -> broken, Check (loot + XP: wiki per creature; "You've caught a
     <creature>." is the OSRS pattern), Dismantle, timeout collapse. Not started.
  2. **Boss kill counts** for KPH Tracker ("Your <boss> kill count is: N."): pick a boss we can
     spawn and kill (Giant Mole, Obor, Bryophyta). ~0.5 day.
  3. **Kitten Tracker**: kitten growth/hunger timers and messages. ~0.5-1 day.
- **Upstream:** patches 29-34 (rune pouch, plank sack, friend add, hard core, puzzle boxes, foes);
  26 regenerated. `git am` of all 34 applies on the pin.
- **Tools (scratch, easy to redo):** item dump (`id|name|gameval|certtemplate|placeholder|...|param623`
  from a throwaway integration test over `ServerCacheManager.getItems()`), the wiki casket parser
  (`?action=raw`, `{{DropsLineReward}}` rarity = product of the `{{#expr:1/( a/b * c/d )}}` factors,
  weights over the LCD), a composite map renderer (if3 `type=model` components drawn with
  ModelDecoder at their x/y/angle/zoom; `k = 512/zoom`, depth-sorted).
- **Device tips:** typed `::` commands sometimes stay in the input: toggle the keyboard (90,220)
  until `mInputShown=true` and send keyevent 66; after a restart or plugin-test.sh reinstall the
  old session lingers ~45 s ("account still logged in"): wait, Try again, Play Now. The host's
  debug panel covers x < ~600 (taps there do not reach the game).
- **Next candidates:** fairy ring favourites + hideout sequence, music areas importer, the
  Awowogei loc anagram (hard), medium/hard leftovers (master clue tertiary), elite clues.

## Latest state (2026-10-17, tenth server dev, on OpenRune)

Protocol unchanged. Details in `docs/openrune-port.md`, "Tenth server dev".

- **Deployed** 9a73dbe (03:32; includes b9001d9 fairy ring dialog abort and 0b2ddee essence pouch
  wording, both still to be device-checked: Ess Pouch by the client dev, the fairy ring search input
  on the next Configure + Teleport).
- **Easy clues complete except the 2 X Marks the Spot map rows (not easy scrolls):** all 71 loc
  searches (open-then-search), Galahad and N Falador maps, "Alone" unlocks on Ice Mountain
  (new music area), Cecilia success device-checked.
- **Fairy rings** (`content/travel/fairy-rings`): device-checked dial/log/teleport/Last-destination.
- **Tools:** a scratch Java renderer for cache models (OpenRune's `ModelDecoder` from the server
  install's lib dir, run in `eclipse-temurin:21` with `.data/cache/LIVE` mounted; top-down for the
  single-model maps, if3 placements for icon maps) identified the map interfaces. Rewrite it from the
  port doc's description if needed (~50 lines).
- **Build tip:** after adding a module, run the build steps with `--no-configuration-cache` (the
  cached configuration skips the new module silently: no compile, no error).
- **Device tips:** chat commands: tap the keyboard toggle (90,220) until `mInputShown=true`, then
  `input text` and keyevent 66, otherwise the line stays typed but unsent; the music tab is the
  harp (1840,685); long-press NPCs after `::freeze`.
- **Medium clues** (d8937ea, 4719636, 9a73dbe): complete on the cache data (167 steps; puzzle
  boxes are hard tier and up). Device-checked with Clue Scroll + Loot Tracker on: anagram read
  (plugin overlay), Baraek hands out the challenge scroll, its Read shows the question, talking
  again opens "Enter amount:", 5 is accepted ("Spot on!"), the challenge scroll goes and the scroll
  swaps to the next step; a coordinate scroll reads "00 degrees 05 minutes south / 01 degrees 13
  minutes east" and the plugin overlay solves it; digging there gives the casket; the medium casket
  opens the reward screen with 5 items and "You have completed 1 medium Treasure Trail.". Not tried
  on the device: double emotes with medium Uri, key steps, map crates, medium STASH.
- **Candidates for server dev #11** (roughly in value order):
  1. **Hard clues** on the same engine (~1.5-2 days, size first): hard rows in the cluehelper
     tables; puzzle boxes (slide puzzle 306: `trail_puzzleinv`, piece objs from enums 1864/1865
     keyed by varp `if1` 1-6, moves are client-side in `trail_slidepuzzle_op` and transmit op1 with
     the slot; the server should mirror the move and check the solved order), light boxes, coordinate
     wizard encounters and the Double Agent for emote steps, hard map interfaces (single-model maps
     20-24 and `trail_clue_hard_map006/007`; the renderer approach works), hard casket from the wiki
     (same parser: DropsLineReward rarities with `{{#expr:}}`).
  2. **Pouch/sack item support** for plugin rows: Rune Pouch (inv + varbits the Rune Pouch plugin
     reads), Plank Sack (hub plugin; check its message regexes first), verify Ess Pouch on the
     device with the client dev after 0b2ddee is deployed.
  3. **Fairy ring extras:** favourites (varbits `fairyring_fave_*`, Favourites op, log fave icons),
     the AIR-DLR-DJQ-AJS hideout sequence, per-ring quest requirements (wiki page lists them).
  4. **Music areas:** only Lumbridge and Ice Mountain have `music_modern` rows; the wiki's
     `Map:<track>` pages give modern-mode polygons (the Alone one was copied by hand); a small
     importer could add many areas (area ids from 32762 down, dbrows near 60920).
  5. Beginner/easy leftovers: scroll cases, the master clue tertiary, worn items into STASH units.

## Latest state (2026-10-17, ninth server dev, on OpenRune)

Protocol unchanged. This session: Treasure Trails rebuilt on the cache's own clue data, beginner
finished, easy added, music tab Play. Details in `docs/openrune-port.md`, "Ninth server dev".

- **Engine** (`content/other/treasure-trails`): `TrailTier` (beginner, easy), `TrailSteps` builds
  every step from Jagex's `cluehelper_*` dbtables (texts, npc/loc/coord targets, emotes, outfits,
  STASH locs): Talk, Charlie, HotCold (Reldo), Search (new `LocOpOverrides` API), Dig (maps,
  coordinate cryptics), Emote (+Uri per tier), Music (Cecilia). Easy scrolls are one item per step
  (item param 623) and are swapped in their slot. `StashUnitScript` (units from the emote rows).
  Caskets per tier from wiki tables (beginner 44,280; easy 617,760). Check steps. `::clue
  [beginner|easy] [row|list] [steps left]`. Tests: `:content:other:treasure-trails:integration`
  (12). Upstream: `21-treasure-trails` (+ `22-music-tab-play`, which 21 needs).
- **Device-checked** (58ec4ba, eda3a16): beginner Uri, Charlie, hot-cold (plugin solver narrows to
  the spot), STASH; easy Talk (scroll swap), Search, coordinate Dig, Emote + easy Uri, easy STASH,
  easy casket and the Loot Tracker entry ("Clue Scroll (Easy)"; the host's event-order fix works).
- **Music tab** (`api/music-plugin` `MusicTabScript`): Play/Unlock hint on the jukebox list; child
  index = non-hidden music rows in row order (from osrs-dumps `music_list_setup`); order
  device-checked (5684e9e deployed 02:10). The success branch of a music step (Cecilia while the
  track plays) is untested on the device: "Alone" did not unlock on Ice Mountain (OpenRune's area
  unlock). Built but not deployed: 1d7c56a (unlock hint wording "This track unlocks ...").
- **Left out:** 4 easy map interfaces (Galahad, N of Falador, behind Lumbridge castle, Draynor
  wheat field: map interfaces are model icons, see the port doc), 27 easy loc searches on locs
  without a "Search" op (closed drawers), scroll cases, the master clue tertiary, medium+ tiers.
- **Other:** new characters start at 3222,3218 (`CharacterAccountApplier`); the game channel closes
  after 15 s without inbound packets (RSProt `IdleStateHandlerSuppliers.Game`): headless clients
  must send NO_TIMEOUT (desktop opcode 75). DB queries: a throwaway `eclipse-temurin:21` jshell with
  `--network container:osrs-openrune` and the install's postgresql jar (port from `ps` in the
  container). The "desktop" account's saved row was 3200,3020.
- **Device tips:** emotes tab = the green dancing figure (1840,600; smiley is Friends); scroll the
  emote list with short swipes; equipment slots: head 1490,540, weapon 1370,707, feet 1490,880,
  ring 1610,880; `::item` takes gameval names (`black_axe`, `woodplank`, `poh_saw`).
- **Next ideas:** medium clues on the same engine (anagrams/ciphers/coordinates/keys/kills/challenge
  scrolls exist in the tables; coordinate clues need a sextant flow or just digs), the remaining map
  interfaces (plot `trail_mapNN` components against wiki images), drawers (open then search).

## Latest state (2026-10-17, eighth server dev, on OpenRune)

Protocol unchanged. This session: beginner clue scrolls for RuneLite's Clue Scroll plugin. Details
in `docs/openrune-port.md`, "Eighth server dev".

- **Module** `content/other/treasure-trails`: Read -> 203 / maps 346-356 with the plugin's exact
  texts; 21 steps (talks, emotes with Uri, map digs) in dbtable `trail_beginner_steps`; reward
  casket from the wiki table; scroll box Open; `::clue [step] [steps_left]`. New API
  `NpcOpOverrides` (a claimed npc op goes to the clue handler ahead of the npc's own Talk-to).
  Tests: `:content:other:treasure-trails:integration` (6). Upstream: `openrune/upstream/21`.
- **Deployed** 00:02 (35d27b9) and device-checked: 203 and map 348 on mobile, plugin overlay and
  NPC marker, Ranael step, dig, casket, reward screen. The Loot Tracker recorded nothing: the plugin
  host reads inv 141 live when it dispatches the ChatMessage late, so the snapshot already holds
  the rewards. That is fixed in the host (client dev), not the server (a one-tick delay was
  reverted). Re-check the Loot Tracker once the host fix lands.
- **Open:** client gap: ClueScrollPlugin's Read handler NPEs (item composition null for the
  inventory CC_OP), so it never learns the clue item id; emote/Uri step not tried on the device;
  Check steps, hot-cold (Reldo), Charlie the Tramp, STASH units, scroll cases not done.
- **Device tips:** `item_action` needs the inventory tab open (tap the backpack at 1757,515); the
  clue scroll's close X is at 1060,362 and the reward screen's at 1215,355; the RL sidebar button is
  at 53,495 (Loot Tracker tab at 307,26).
- **Build tip:** after adding a module, `openrune.sh build` can reuse a stale configuration cache and
  regenerate `api/generated` without new dbtables; run `openrune.sh gradle
  --no-configuration-cache :or-cache:buildCache` and then the remaining build steps.
- **Next ideas:** the Uri emote step on the device; easy clues (same engine, more rows and the
  easy casket table); hot-cold device for beginners (Reldo, `beginner_device` 23183, temperature
  messages HotColdClue parses).

## Latest state (2026-10-16 late night, seventh server dev, on OpenRune)

Protocol unchanged (240/253 mapped, 239 verified). This session: farming for RuneLite's Time
Tracking, and the bird house device check. Details in `docs/openrune-port.md`, "Seventh server dev".

- **Deployed** 22:11: everything up to 3f46725 (farming). Device-verified: rake, plant, growth on
  the clock, `::growpatches`, harvest/pick at Catherby, full-inventory stop; bird houses (Build,
  Seeds, `::birdhouses`, Empty, Loot Tracker message) at the southern space.
- **Farming** (`content/skills/farming`): allotments, flower and herb patches at Catherby, Falador,
  Ardougne, Morytania, Hosidius and Civitas illa Fortis (all deployed 22:41 and checked in game);
  server-only dbtable `farming_crops`, varps 63780-63795, timer 460, `::growpatches [cycles]`.
  Values checked against client-1.13.1.jar's PatchImplementation by reflection. Tests:
  `:content:skills:farming:integration` (8). Upstream: `openrune/upstream/19-farming-patches.patch`.
- **Open:** Time Tracking's farming tab kept "Done at 22:30" (the raked-weeds prediction) after
  planting; expected 22:50. Server values were right on the client (`get_var` 4772 = 6, then 7).
  The client dev fixed it in 7d1ff62 (the sidebar did not activate the selected panel); not re-checked
  on the device by the server side. Time Tracking is not in the default plugin
  set: `tools/dex-plugin.sh --core timetracking re/host/plugins-extra/timetracking.jar`, then
  `EXTRA="timetracking" tools/plugin-test.sh` (add `devtools` and the prop for devtools).
- **Next (approved): C, beginner clue scrolls for the Clue Scroll plugin (~1.5 days).** What the
  plugin keys on (`re/rlsrc/.../cluescrolls/ClueScrollPlugin.java`): a "Read" menu click on an item
  whose name starts with "Clue scroll" (beginner = obj 23182, `trail_clue_beginner`), then
  `WidgetLoaded` of group 203 with the clue text in component 203:2 (13303810), matched against the
  plugin's text tables (AnagramClue, CrypticClue, EmoteClue, ... beginner entries); beginner map
  clues are whole interfaces 346-356 (`BeginnerMapClue.forWidgetID`); NPC steps use DIALOG chat
  ("sender|message"); a chatbox item widget 12648449 showing 23182/19835 resets the clue. Plan:
  (1) clue item + Read opening 203 with one beginner text, and one map clue interface; ask for a
  device slot right away to settle the mobile layout of 203 and 346-356 (the main risk);
  (2) steps: a few beginner anagram/cryptic NPC talks, an emote step, a map dig with a spade, a
  coordinate-free dig; step state in server varps, step data in a dbtable; (3) reward casket
  (beginner) from the wiki table, the "You have completed N beginner Treasure Trails." message;
  (4) a drop source (implings already roll clue tertiaries; also `::clue`). Not the hot-cold
  device, not STASH units.
- **Device tips:** devtools `interact_object` works with `objectId` (farming patch and bird house
  base locs have no name, so `nameFilter` fails); inventory "Use" works with a long-press on the
  item, tap "Use ...", then tap the loc. Bird house space 2 is at 3679,3815; stand at 3679,3814.
- **Next ideas:** farming slice extensions (compost bin 4775, other farms: Ardougne, Morytania,
  Hosidius are just more `Area` rows plus their loc names; tool leprechaun; disease); bird house
  nest chance fixed (per mille, wiki chart; deployed 22:41) and bird houses are `upstream/20`;
  clue scrolls (beginner/easy slice).

## Latest state (2026-10-16 late night, sixth server dev, on OpenRune)

Protocol unchanged (240/253 mapped, 239 verified; only C->S 45 SET_HEADING open). This session was
content for RuneLite plugins plus upstream patches.

- **Deployed** (21:40): everything up to 63f453b. Device-verified: Genie lamp Rub (xpreward +
  Confirm, 990 XP at 99 = 1x), impling jar Loot (dragon and crystal jars), crystal impling catch
  in Prifddinas. Not yet on the device: bird houses.
- **Commits:** be36cc2 Slayer streak varbits + OSRS gem/completion wording; c39fca0 jar Loot (wiki
  tables); 145fae7 crystal implings; f332f42 Loot on the cache's op ("Loot" is the jars' THIRD iop:
  the client sends If3Button op 4 -> HeldOp3); 79d641b xpRate forced to 1.0 at login (the realm gave
  new accounts 150x; existing saved XP stays); 63f453b bird houses; 6737557 openrune/upstream/.
- **Slayer:** server side complete (varps 394/395/2096/4258, varbits 4067/4068/4069; Turael gives
  tasks, gem Check). The plugin needed Client.getDBRowsByValue on the host (client dev, 3ea9b49).
- **`starts` rows checked:** NPC Aggression Timer, Player Indicators and Emojis need nothing from
  the server (aggression timer: client config `npcUnaggroAlwaysActive` or a name pattern; Player
  Indicators: a second player via `tools/multiclient/desktop_login.py`).
- **Bird houses** (`content/skills/hunter/.../BirdHouseScript.kt`, dbtable `birdhouse_types`,
  server varps 63760-63767 in the hunter pack): spaces at 3677,3882 / 3678,3815 / 3763,3755 /
  3768,3761 (multilocs birdhouse_1..4 on varps 1626..1629). Device check pending: the space wasn't
  found with a long-press near 3677,3880 (try a tap beside the wall to the north, or the southern
  spaces). `::birdhouses` makes seeded houses full.
- **Upstream:** `openrune/upstream/` has 18 format-patch files + README (apply-checked alone and
  together on the pin, compiled together, tests and spotlessCheck pass). Patch 15 (implings) was
  regenerated after the Loot op fix; bird houses are NOT in it yet (add them to 15 or a new 19 from
  the scratch clone approach in that README).
- **Gotchas:** OpenRune's AGENTS.md (in openrune/src) wants game data in dbtables, few comments,
  and tests for anything that moves items. Content tests that drop items to the ground need
  `player.observerUUID = 793L`. `onOpHeldU(clockwork, logs)` was already taken by crafting (an
  existing feature makes registration throw at startup; the integration tests catch that). On the
  device, devtools `item_action`/`interact_object` currently fail ("No menu entry matching"); taps
  and long-presses work. After any restart: wait ~30 s, "Try again", screenshot, Play Now.
- **Farming (proposed next, ~1 day, waiting for the orchestrator's OK when this session ended):**
  OpenRune has no farming. RuneLite's Time Tracking reads per-region `farming_transmit` varbits
  4771-4775 (`re/rlsrc/.../timetracking/farming/FarmingWorld.java`: Catherby region 11062 = 4771/4772
  allotments, 4773 flower, 4774 herb, 4775 compost; Falador 12083 the same set) and decodes each
  value with `PatchImplementation.java` (e.g. allotment 0-3 weeds (3 = raked clear), potato 6-9
  growing, 10-12 harvestable; herb guam 4-7 growing, 8-10 harvestable). The patch locs are
  multilocs on those varbits. Plan: rake/plant (seed dibber)/grow on the farming clock
  (allotments 10 min/stage, herbs 20, flowers 5)/harvest, crop data in a dbtable, a fast-forward
  command; no disease, compost, water or payments.
- **Next ideas (sizes):** bird house device check (5 min); farming patches for Time Tracking
  (1.5-3 days: planting, growth cycles, the farming_transmit varbits per region); clue scrolls for
  the Clue Scroll plugin (1-2 days for a beginner/easy slice: clue items, the clue interface text,
  dig/talk/emote steps); lucky impling loot (clue reward tables).

## Latest state (2026-10-16 night, fifth server dev, on OpenRune)

Metric at the end: mapped 240/253, verified 239/253 (`tools/progress.py --working --no-history`).
The only unverified opcode left is C->S 45 SET_HEADING, probably sailing's boat heading from the
minimap, which neither server has. The 13 unmapped ones are the dead `OP_n`.

- **Deployed:** osrs-openrune runs the build from dca5be0 (deployed 19:41). It has the MIDI_SWAP
  encoder, decoders 8/20/28/50/57/84, implings, the Genie, `::trace` and the URL_OPEN fix. 72efaa4
  (OP_0: the heatmap encoder removed) is built but not deployed; it changes nothing at runtime.
  Deploy: `cd openrune && docker compose --env-file switch.env up -d --build --no-deps openrune`
  ("OK restart" first). Boot-check a build without touching the live container: `docker build -t
  osrs-openrune:bootcheck .` and run it with `--rm --tmpfs /app/.data/postgres:uid=1000`.
- **Protocol this session:**
  - 24 URL_OPEN: the reader `0x5da16c` ISAAC-decrypts opcode 0x18's payload. Our
    `AndroidDelegatingEncoder` dropped the desktop encoder's `encryptedPayload` flag, so the stream
    cipher desynced (stock client: session drop; with our hooks: crash). Fixed and verified live:
    the external-link dialog shows, and CANCEL keeps the session.
  - 148 CAM_SKYBOX: verified statically (renderer sky slot set `0x7d971c` / clear `0x7d97c8`).
  - 69 OPWORLDENTITY6: verified statically (menu builder `0x633a1c`).
  - S->C 0 is `OP_0 // dead:` (no dispatcher case).
  - `::trace` saw C->S 8 (music slider) and 46 (jingle end) live.
- **Device tools:** the devtools MCP works for server checks (`tools/devtools_mcp.py --call
  tools/call '{"name":"type_chat","arguments":{"text":"::tele 2592 4320"}}'`, `list_npcs`,
  `interact_npc`, `get_dialogue`; setup in docs/plugin-host.md). `::trace` logs every incoming
  packet, consumed or not, as `IN op=.. len=.. Class hex` in `docker logs osrs-openrune`. Typing
  without devtools: `server/tools/device.sh cmd "::url ..."`.
- **Content (verified on the device):** the Genie random event (`::randomevent`: spawns beside the
  player, faces them, calls them by name; Talk-to gives the lamp, Dismiss works) and impling
  catching (Puro-Puro, `::tele 2592 4320`, net wielded, `::npc 300 ii_impling_type_1_maze`).
  Puro-Puro's implings spawn by themselves. Lamp Rub is not tried on the device.
- **Docs:** upstreamable OpenRune fixes are listed in `docs/openrune-port.md`, "Fixes worth
  contributing upstream".
- **Next ideas:** 45 needs sailing content; the plugin-host `starts` rows left (Slayer, NPC
  Aggression Timer, Player Indicators, Emojis) look client-side or just unchecked; Slayer content
  exists in OpenRune (get a task from a master); crystal implings (Prifddinas) and jar looting are
  not done.

## Latest state (2026-10-16 evening, fourth server dev, on OpenRune)

The switch is done: the default server is OpenRune (`openrune/`, osrs-openrune on 43594, sidecar on
8080). Protocol work stays in `server/rsprot.patch`. Metric at the end of the session: mapped
241/253, verified 226/253, implemented 226/253.

Done this session:
- **Clan/group bodies verified** (f993c6a): the client's own parsers for 19/84/87/88 and
  134/138/140/141 were compared field by field with RSProt's desktop encoders, and all match
  (details in `docs/android-protocol.md`). The four clan encoders are bound.
- **NPC "invisible spawn"** is not a protocol bug. `::npc` spawns on the player's tile, and the NPC
  hides inside the player model. On a long-press it is already in the menu. NPC info tests:
  `NpcInfoViewEdgeTest` (RSProt desktop tests). The spawn-clock A/B was reverted.
- **Quest-pack CS2 crash fixed** (c391951): OpenRune's packer gave added clientscript groups zero
  sizes in the reference table. `openrune.sh build` runs `scripts/fix_reftable.py` and
  `add_varcs.py`, and only the spawn pack's scripts stay out. Verified on the device. The quest
  tab filter itself was not looked at yet.
- **Bankers** (00127f7): every Banker type gets `content.banker`, so Bank/Collect/Talk-to work
  across the booth (was "I can't reach that!"). Verified on the device: Bank on the Lumbridge banker
  across the booth opens the bank. The quest-list filter is verified too (7/11 quests).
- **URL_OPEN 24**: an encoder is bound (`::url`). A live `::url` dropped the app to the login screen.
  The client reads exactly one string and calls `ApplicationContext.openBrowser`/
  `openOverlaidBrowser`, but no activity starts (logcat): the client closes the session itself. Still tagged.

Tooling from this session (scratch, not in the repo, easy to redo):
- CS2 decoder: trailer of 16 bytes (int/string/long locals and args), name string first;
- headless jshell against `openrune/src/server/app/build/install/app/lib` (mount it somewhere other
  than `/lib`), e.g. `ServerCacheManager.INSTANCE.init(241)` from `/w` = `openrune/src` to read
  server NPC types, or `AndroidGameMessageEncoderRepository.INSTANCE.build(new
  BlockingHuffmanCodecProvider())` to check that the encoder bindings load;
- RSProt tests in Docker as root (server/rsprot build dirs are root-owned): `docker run --rm -e
  GRADLE_USER_HOME=/cache -v openrune-gradle8:/cache -v $PWD/rsprot:/src -w /src gradle:8.14.3-jdk21
  gradle --no-daemon -q :protocol:osrs-241:osrs-241-desktop:test --tests '*NpcInfo*'`, then chown
  the volume back to 1000;
- building a test cache without touching the deployed one: copy `openrune/src` to a scratch dir and
  run `./gradlew :or-cache:buildCache` there (the Docker image copies `src/.data` at deploy).

Later in the session (all static, from the client's own code; details in `docs/android-protocol.md`):
11, 120, 123 (MIDI_SWAP encoder added) S→C; 8, 20, 46, 50, 84 C→S (decoders bound for 8, 20, 50,
84). Verified 234/253 by tag count (progress.py's ApiCoverage step was failing while the client
dev's host build ran).

Remaining unverified (5; 28/57 settled afterwards by their CS2 op callers). Live-slot plan, once
the devtools MCP can drive the client: deploy the pending installDist first (MIDI_SWAP encoder, decoders
for 8/20/28/50/57/84); then send a PM (50) and trigger a settings IfScriptTrigger (8; OpenRune's
AudioSettingsScript uses one) while watching `docker logs`; then try the compass/minimap modes for 45.
- S→C 0 heatmap: the dispatcher ignores 0.
- S→C 24 URL_OPEN: one gjstr to bridge slot +0xf0, but the client drops the session; no browser
  activity starts.
- S→C 148 CAM_SKYBOX: an int at client+0x4ac*8; -1 calls FUN_007d97c8.
- C→S 45 SET_HEADING: sender FUN_00623960 writes -heading and also applies it locally; called from the
  minimap handler (0x68d7b8) and a pointer thunk (0x61ccf8). The UI that triggers it is unknown.
- C→S 69 OPWORLDENTITY6: sender FUN_00623550 writes an id g2Alt3, next to the other examine senders,
  but is reached only indirectly. Found with a 24-instruction scan window; the 6-instruction one missed it.

GROUP_FULL caveat: the client has a third (scope-2) group var list.

## Earlier state (2026-10-16, third server dev; new hashes after the authorship rewrite)

Container runs ac31a14 (deployed 23:34 local); 39e7f91 after it is tags/docs only. Metric
(`tools/progress.py --working`; revert its output files, they belong to the orchestrator): mapped
241/253, live opcodes 241/241, verified 218/253.

Test commands added this session: `::trace` (log every incoming message, Event* included),
`::jingle id`, `::chatfilter pub trade` (78), `::give id [n]` (alias of `::item`), `::proj id dx
mode`. Device tips: camera rotation = `input keyevent --longpress 21/22/19/20` (one-finger swipes
only send mouse events); after a container restart the app can hang at "Connecting to server..."
with no requests: `am force-stop com.jagex.oldscape.android`, relaunch with monkey, tap Play Now
(958,550). The client dev's Quest Helper sidebar may be left open and swallow typed input.

Next steps: remaining unverified entries (see `grep unverified` in the two enums): 45 SET_HEADING
(special minimap mode), 46 SOUND_JINGLEEND (needs audio on), 11 (labels need a script redraw:
find the chat-tab script and run it after the packet), 24 URL_OPEN, 120 IF_SETROTATESPEED, 123
MIDI_SWAP, 148 CAM_SKYBOX, clan/group bodies (19/84/87/88, 134/138/140/141), 8 IF_SCRIPT_TRIGGER,
50 MESSAGE_PRIVATE. Untested on device: Spin at the castle wheel via door 1543, Fred's gate
double-open.

- **Persistence** (7ee343a, verified live: survives relog and `docker compose restart`):
  `Saves.kt` writes one JSON file per account (Gson) with position, HP, XP, varps, inventory,
  worn and bank to `SAVE_DIR` = named volume `rsps-saves:/saves`. Loaded at login (starter kit only
  for new accounts), saved on removal, every 50 ticks and on JVM shutdown. Inspect with
  `docker exec osrs-rsps-test cat /saves/tester.json`; delete it for a fresh character.
- **Loc reach** (7ee343a): solid locs (footprint flagged LOC) need a side tile with no wall in
  between (`Collision.reachesRect`); walls/doors keep "any adjacent tile".
- **Stairs** (b2f7660): no destination table; Climb-up/down (and "Climb" with an up/down choice,
  "Top-floor", "Bottom-floor") move one level and land on the nearest open tile that has a floor
  surface (`Collision.nearestOpen`, SURFACE flag from terrain underlay/overlay). Verified live:
  Lumbridge castle stair 56230 lands at 3205,3209 level 1. Sheep Shearer route (headless):
  stairs from 3205,3209 -> Open door 1543 at 3207,3214 -> Spin the wheel 14889 at 3209,3212 from
  3209,3213. Spin itself not yet tried on the device.
- **Gates** (d928009): opening one half of a double gate (e.g. Fred's pen 12986/12987 at
  3186,3268-3269) opens the other half too.
- **Protocol**: verified live 76 PROJANIM_SPECIFIC_V4 (start height 160 was off camera; use
  `::proj 100 4 0`) and 115 HIDEOBJOPS. C->S 6 OPOBJ6, 80 HISCORE_REQUEST, 90
  UPDATE_PLAYER_MODEL_V2 named; sender-less C->S 1/12/27/62/73/76/83/85/87 and ignored S->C
  110/116/137 tagged `// dead:` (orchestrator ruling: dead opcodes stay OP_n; name only with
  per-opcode evidence). 11 CHAT_FILTER_SETTINGS_PRIVATECHAT still shows no effect.
- **RE helpers**: decompile with `analyzeHeadless re/ghidra osclient -process osclient.so
  -noanalysis -readOnly -scriptPath tools/ghidra_scripts -postScript Decompile.java 0x<addr>`
  (Ghidra image base 0x100000 = file VA + 0x100000). Every C->S sender: scan the text segment
  for `bl 0x79cc90` with capstone (`~/tools/venv`), opcode = `mov w0, #imm` before it.
- Headless map checks: jshell in `eclipse-temurin:21` with `build/install/*/lib/*.jar`, mount
  `data/cache` at `/data/cache`, redirect stdin from /dev/null (else it hangs).
- On hold until the orchestrator relays the RSMod evaluation verdict: generic stairs table, new
  quests. Keep server work small; protocol verification carries over to any engine.

## Earlier state (container ran e9b03e2, deployed 2026-10-15 21:39 local)

- A rev-241 test server runs in Docker (`osrs-rsps-test`): JS5 cache (OpenRS2 cache 2735),
  `jav_config.ws` / `slr.ws`, login, a small game world, reconnect (60 s grace), and a local
  OpenID sign-in, so no Jagex account is needed.
- The official Android app (build 241.4, `ENHANCED_ANDROID`) works against it:
  - Lumbridge gameframe, appearance, stats and run energy.
  - Walking, public and overhead chat, `::` commands, logout.
  - Inventory with the starter kit; item options by name (Wield/Wear/Equip, Eat/Drink, Drop).
  - Equipment tab with unequip.
  - Four wandering courtyard NPCs (Hans, man, woman, chicken) with say, animation and transform.
  - SERVER_TICK_END every tick, which drives GameTick in the plugin host.
  - Collision + pathfinding, melee combat with NPC death/respawn, journal tab (quest list), and
    Cook's Assistant end to end (see Next steps).
  - OPNPC/OPLOC/OPOBJ 1-5 decoded (layouts from the client's senders). The player walks next
    to the target (straight line), then: Talk-to opens an NPC chat, Bank opens the bank, Open on
    a door removes it (zone LOC_DEL), Take picks up an owned ground obj; other ops print the
    NPC/loc/obj name and op. Verified live: OPLOC1 (door 1535) and OPLOC6 (examine).
  - Zone packets: UPDATE_ZONE_PARTIAL_ENCLOSED with LOC_ADD_CHANGE_V2 / LOC_DEL / OBJ_ADD /
    OBJ_DEL, plus top-level LOC_ADD_CHANGE_V2 / LOC_DEL (verified: tree added/removed, zone obj
    shown and taken).
  - Bank (bankmain 12 in main modal 601:27, bankside 15 in side modal 601:114, inv 95) with
    withdraw (op 1 = one, else all) and deposit; verified. Open with `::openbank` (the client
    swallows `::bank` and says a joke line in public chat instead).
  - Dialogues: NPC (chat_left 231), player (chat_right 217, IF_SETPLAYERHEAD), options
    (chatmenu 219 via script 58; resumes as 219:1 sub = option). Only two option rows showed
    for three options (scroll? unverified).
  - NPC and loc configs decoded for names/ops (`EntityType`); rev-241 loc configs use opcodes 6/7
    for int model ids.
- The Android protocol differs from desktop: opcodes are permuted and field layouts differ per
  packet. It is implemented as a new `osrs-241-android` module inside a vendored RSProt
  (`server/rsprot`, gitignored), kept as `server/rsprot.patch` against upstream
  `4ec57fd7a28e16e9ae207bd1f02b7f343bb77957`. Upstream has no Android support and nothing public
  maps the Android protocol.

## Layout

| Path | What |
|---|---|
| `server/src/main/kotlin/rsps/Main.kt` | Boot: cache, JS5 store, huffman, gameframe enums, obj types, RSProt network factory, message listeners |
| `server/src/main/kotlin/rsps/World.kt` | Game loop (600 ms), login/reconnect, players, NPCs, movement, inventory/equipment, commands |
| `server/src/main/kotlin/rsps/Cache.kt` | dat2/idx reader, containers, reference tables, JS5 responses + master index |
| `server/src/main/kotlin/rsps/GameData.kt` | Enum decoder, gameframe (toplevel 601 mobile via enum 1745), obj names/iops/wearpos |
| `server/src/main/kotlin/rsps/Http.kt` | Raw-socket HTTP: jav_config (from `protocol/jav_config.ws.template`), slr.ws, /rsa |
| `server/src/main/kotlin/rsps/LocalAuth.kt` | Local OpenID provider + game-session API (`LOCAL_AUTH=true`) |
| `server/src/main/kotlin/rsps/Config.kt` | Env config; RSA key in `server/data/rsa.properties` (gitignored) |
| `server/rsprot.patch`, `server/scripts/rsprot.sh` | `init` clones the pinned RSProt and applies the patch; `export` writes working-copy changes back |
| `rsprot/protocol/osrs-241/osrs-241-android/` (inside `server/rsprot`) | Android prots, encoders, decoders, player/NPC extended-info writers |
| `tools/patch_server.py` | Patches the game .so: RSA modulus + config/world-list URLs (56-char, contain "jagex.com") |
| `tools/patch_local_auth.py` | Patches base APK smali/res for local sign-in (cleartext, AppAuth HTTPS checks) |
| `tools/repack.sh` | Shared with the client session; `SERVER=` / `LOCAL_AUTH=1` are the server-side options |
| `native/src/net_trace.*` | Socket/DNS logging hook (`setprop debug.osrsrl.nettrace 1`) |

## Build, deploy, test

```
cd server
scripts/rsprot.sh init               # once, recreates server/rsprot from the patch
./gradlew-docker installDist         # compile check (Gradle in Docker, cache volume rsps-gradle-cache)
docker compose up -d --build         # deploy (~1-2 min); logs: docker logs -f osrs-rsps-test
scripts/rsprot.sh export             # after editing server/rsprot: save to rsprot.patch before committing
python3 tools/probe.py 127.0.0.1 43594   # headless JS5 check (all archive CRCs)
LOCAL_AUTH=1 SERVER=10.0.2.2:8080 ADB=/mnt/c/Users/Alex/AppData/Local/Android/Sdk/platform-tools/adb.exe \
    ANDROID_SERIAL=127.0.0.1:5555 tools/repack.sh --install     # client (run bare, from repo root)
```

In-app: "Sign in with Email / Username", close the privacy screen (X top right), then Play Now
("Tester"). After a container restart, tap "Try again" first, then Play Now. Look at a
screenshot before tapping blind: on another screen the same spot is "Sign out".

## How to identify Android packets

- **Server→client:** each opcode is a `case` in the dispatcher (Ghidra `0x541dd4`, decompiled to
  `re/decomp/00541dd4.c`; the scratch helper split it by case). Match the reads against
  RSProt's desktop encoder for the same message, then write an Android encoder using the
  transform table in `docs/android-protocol.md`. Sizes come from the table at .rodata `0x104c4c`.
- **Client→server:** all 102 opcodes are framed from the client's size table. Unknown ones arrive
  as `AndroidUnknownPacket` and are logged (`LOG_ANDROID_PACKETS=true`). Perform the action in
  game and read the bytes. Many matched desktop byte for byte (delegate to the desktop decoder).
- Extended info: player (`0x669a04`) and NPC (`0x569778`) readers list flag tests in read order.

## Open work

- **Dialogue works** (`::talk`, continue closes it); closing also runs script 216 (chat_build) so
  the chat history comes back.
- **Ground items** (`::obj`, `::zobj`, drop, Take) are shared zone OBJ_ADD/OBJ_DEL, resent after
  every rebuild. (OBJ_ADD_SPECIFIC, no longer used, needs timeUntilDespawn > 0.)
- `server/tools/device.sh login|cmd|shot`: BlueStacks helpers (login after restart, chat commands
  with the keyboard/Enter workaround, screenshots).
- Relogin with the same name replaces the old session (fixes a ghost duplicate player).
- Component ids come from rev-239 symbols and can be off in rev 241 (chatmodal 566 -> 568):
  verify against the cache (scratch tool: decode interface components, parent chain).
- Quest/var commands: `::quest name [start|done|reset|n]` (name substring; adb `input text`
  breaks on apostrophes, so use e.g. `::quest assistant done`), `::varp id [v]`, `::varbit id [v]`,
  `::hit [n] [type]`, `::npchit [n]`, `::events [e1] [e2]` (resend inventory IF_SETEVENTS).
- Commands: `::xp skill n`, `::talk [npc] text`, `::obj id [n] [dz]` (OBJ_ADD_SPECIFIC on your tile),
  `::zobj id [n]` (zone OBJ_ADD), `::locadd id [shape] [rot]`, `::locdel [shape] [dx] [dz]`,
  `::openbank`, `::ptalk text`, `::options a b c`, `::freeze` (toggle NPC wandering, makes NPCs tappable),
  `::walk x z`, `::combat [lvl]`, `::heal`; in-game name = `LOCAL_DISPLAY_NAME`.
- Tapping on the device: a plain tap often walks; long-press (`input swipe x y x y 1000`) opens the
  minimenu. The default camera is far out and NPC overlay labels are offset from the models.
- Component ids: RuneLite's `runelite-api-1.13.1.jar` in `re/runelite` has gameval `InterfaceID`
  constants that match rev 241 (chatbox:chatmodal = 162:568); `javap -constants` on the nested class.
- Docker Desktop may not be running after a Windows restart: start it with
  `cmd.exe /c start "" "C:\Program Files\Docker\Docker\Docker Desktop.exe"`; the container restarts by itself.
- adb input: `input text` always works, but keyevents (Enter) only while the soft keyboard is
  shown (`dumpsys input_method | grep mInputShown`; tap 90,220 to toggle it).
- Unmapped: most NPC/player extended-info blocks (facing, spotanims, ...), WORLDENTITY_INFO,
  zone packets (loc/obj updates), interface text/events beyond IF_SETEVENTS, the CRC order in the
  Android login block (CRC validation is off).
- Client→server still unknown: 10 (window status?), 13, 18, 21 (mouse click), 37 (keyboard), 67, 97, 98, 100.
- NPC extended info `0x80` is spotanims (count `-b`, 8 bytes each, `0x5714e8`). NPC hits are
  0x40000 and headbars 0x1000000; player hits 0x40000, headbars 0x20000 (see android-protocol.md).
- The OutgoingMessageSizeEstimator error "Encoder not registered for AndroidUpdateZonePartialEnclosed"
  is harmless (the estimator only knows the desktop repository); the packet is still sent.

## Next steps (priority order, driven by Quest Helper on the client side)

Deployed and verified on device (2026-10-16, ~00:20 UTC, commit 5c5aa00): collision and
pathfinding (a closed door blocks, an opened one lets you through, routes go around walls; Talk-to
the Cook from the hallway walks round through the kitchen's south door), melee combat, chat
history redrawn after dialogues, the journal tab, and the full Cook's Assistant flow.

Done, for reference:
- **Collision** (`Collision.kt`): flags per map square loaded lazily from archive 5 (file 0 terrain,
  settings bit 1 = blocked, bit 2 on level 1 = bridge; file 1 locs: walls by shape/rot, shapes 9-11
  solid by size, 22 if interactType 1). BFS in a 128x128 window (closest reachable tile within 10
  when unreachable). Used by MOVE_GAMECLICK, all OP* interactions, NPC wandering and chasing; doors
  update it. Headless check: jshell in `eclipse-temurin:21` with `build/install/*/lib/*.jar` on the
  class path (`new rsps.Collision(cache, locTypes)`, `findPath`, `flags`). `::walk x z` paths there
  and prints the step count. Not handled: bridges beyond the level shift, NPC size > 1, players.
- **Combat** (World.kt "Combat" section): OPNPC "Attack" -> melee every 4 ticks when orthogonally
  adjacent (no wall between); OSRS hit chance, max hit from strength (+5 with a weapon), attack/HP
  XP; NPC block/attack/death anims (human 422/424/836, chicken 5387/5388/5389); NPC stats from
  config opcodes 74-79; NPC retaliates and chases within 8 tiles of spawn; auto-retaliate when idle;
  NPC death -> hidden (`setInaccessible`) + drops (bones; chicken: raw chicken, feathers) -> respawn
  after 25 ticks; player death -> home, full HP. Varp 1306 = 1 at login (without it the client hides
  "Attack" on NPCs). `::combat [lvl]`, `::heal`. Not done: facing (rev-241 unified "face" block is
  not mapped on Android), equipment bonuses, ranged/magic, combat styles interface.
- **Journal tab**: `side_journal` (629) is empty unless the server opens a panel into 629:43
  (summary 712 at login; the subtab buttons 629:2-25 arrive as IF_BUTTON and open 712 / 399
  questlist / 259 area_task) and sets varbit 8168. Quest list colours follow the quest varps.
  "Completed: 0/0" and the quest point total (x/0) show 0 totals: probably a varc/varp the
  server should set (unverified).
- **Dialogue engine**: `npcSay` / `playerSay` / `choose` with continuation lambdas (`Player.onResume`);
  `resumePause` runs the next step or closes. `cookDialogue` follows the real Cook's Assistant
  (varp 29: 0/1/2; ingredients 1927/1944/1933; quest points + 300 cooking XP). Cook 4626 spawns at
  3209,3215. Add more quests the same way.

Done 2026-10-16 (commits 9c34817, 47f6549, deployed ~21:17 local):
- Journal totals verified on device: summary "Quests Completed: 0/187", quest list "Completed:
  0/187", "Quest Points: 0/347" (varbits qp_max 1782, quests_completed_count 6347,
  quests_total_count 11877, sub/miniquest counts; `Quests.totals` from the quest dbtable).
- RSA key is baked into the image (`COPY data/rsa.properties`, `RSA_FILE=/app/rsa.properties`):
  under Docker Desktop the `./data` bind mount came up as an empty VM directory and the key was
  regenerated on restart. Current key: modulus 8555c56c... (host `server/data/rsa.properties`; the
  old c72642... key is in `rsa.properties.c72642.bak`). Note: `docker cp` reads the host path, use
  `docker exec cat` to see what the container really has.
- Protocol mapping push: S→C 140/151, C→S 90/102, zone 15/15 (see android-protocol.md; enum
  entries tagged "dispatcher case read" / "sender read" / "unverified: reason").
  `AndroidMoreEncoders.kt`, `AndroidMoreDecoders.kt`. rsprot core change: the decoder repository
  keys message classes by opcode (`getMessageClass(opcode)`), since one delegating decoder class
  is bound under many opcodes. New client messages without game logic go to `World.incoming`
  (logged once per class). Decoded live: 10, 13, 21, 37, 67, 98, 100.
- Inventory IF_BUTTONX ops: 2/3/4 = iop1-3, 5 = Use, 6 = iop4, 7 = iop5 (Drop), 10 = Examine
  (bucket of milk has iop4 "Empty", so Drop came as op 7). "Empty" implemented.
- `::quest name|index [state]`: exact name, list index, unique prefix, then unique substring.
- `::zfx mapanim|locanim|sound|proj id ...` sends the new zone prots (untested on device).

Verified on device 2026-10-16 21:24 (deployed):
- **Chatmenu clipping / hit offset fixed**: chatbox:chatmodal 162:568 is 479x96, chatmenu root 219:1
  is 479x122 at y=12, and script 58's mobile layout (gosub 1972: varbit 6352 osm_simulate or
  client op 6519 == 7) puts 4 options at y=25/48/71/94 (23 px), so rows below ~84 are clipped;
  the client dev saw taps on row 1 resume sub=2. Script 113 (chat layout) sizes 162:568 to fill
  its parent when varbit 10670 CHATMODAL_UNCLAMP = 1 (else 479x96). `setChatmodalUnclamp` sets it
  for chatmenu (and back to 0 for chat_left/right and on close), then runs script 113. Test: Talk
  `::options One Two Three Four`: all four rows visible, taps give sub 1/2/3/4, chat history back
  after closing.

Deployed 2026-10-15 21:39 local (e9b03e2) and tested on device the same session:
- Verified: journal Combat Level 3; facing (player and Woman turn to each other on Attack, player
  faces Romeo on Talk-to); Sheep Shearer start dialogue + Shear gives wool; Romeo start dialogue
  sets varp 144 = 10.
- Not yet tested on device: spinning at the castle wheel, Fred hand-in/completion, R&J stages
  after 10. Sheep stay woolly after shearing (no transform to the sheared NPC yet). From Fred's
  house the pen sheep are "I can't reach that!" (pen gate closed; collision is right, there is
  no gate handling yet). Test setup without the dialogue: `::quest 149 start`, `::item 1735`.

Details of what was deployed:
- 0a7b20e journal summary combat level (script 3954 sets varc 52 COMBAT_LEVEL; was -1).
- 25582e5 facing: player face = Android flag 0x40 (desktop payload, flag p1Alt3); NPC face = flag
  0x40 with a raw flag byte. Player faces NPC on attack/auto-retaliate/any NPC op; NPC faces its
  attacker, resets on death or when it gives up the chase.
- Sheep Shearer (varp 179, end 21): Fred the Farmer 732 at 3190,3273, sheep 2786-2788 in the pen
  (Shear needs shears 1735, gives wool 1737), any loc op "Spin" turns all wool into balls 1759
  (Lumbridge castle level 1 wheel), Fred takes balls (varp = 1 + balls), done at 21 (1 QP, 150
  Crafting XP, 60 coins). Fred hands out shears when the quest starts. Loc ops "Climb-up" /
  "Climb-down" move one level in place (no stair destination table yet).
  Test: `::tele 3190 3273`, Talk-to Fred, shear, climb to the castle wheel, spin, hand in.
- Romeo & Juliet (varp 144: 0/10/20/30/40/50/60/100, abridged dialogue; stages checked against Quest
  Helper's RomeoAndJuliet.loadSteps in re/hub/quest-helper.jar): Romeo 5037 at 3211,3422, Juliet
  5035 at 3158,3427 level 1 (QH: stairs 11797 at 3157,3436), Father Lawrence 5038 at 3254,3483, Apothecary 5036 at 3195,3403; message
  755, cadava berries 753 (loc op "Pick-from" on a "Cadava bush"), cadava potion 756.

Open, in priority order:
1. Quest Helper gaps from the client agent (Cook's Assistant end to end).
2. Finish testing Sheep Shearer (wheel, hand-in) and Romeo & Juliet stages; sheared-sheep
   transform; gates (pen gate); stairs destination table; more quests on the dialogue engine.
3. Remaining S→C unknowns: 20/113/115 (HIDE*OPS / heatmap flags), 65/110 (one string), 143 (p4),
   codecs for REBUILD_REGION_V2 (41), OBJ_CUSTOMISE_SPECIFIC_V2 (132), PROJANIM_SPECIFIC_V4 (76),
   clan bodies (19, 84, 87, 88). C→S: 12, 87, 27, the 11-byte (1/62/76/85) and 15-byte (73/83)
   U/T groups need live captures; EVENT_KEYBOARD delta transform.

Scratch helpers (Python cache reader with reference tables, CS2 disassembler with opcode names,
if3 geometry decoder, script reference scanner) were in the session scratchpad; small to rewrite.
RuneLite gameval VarbitID/VarPlayerID (javap -constants on re/runelite/runelite-api-1.13.1.jar)
names vars quickly (e.g. 10670 CHATMODAL_UNCLAMP).

## Coordination

- Device and restarts (2026-10-15 lock procedure): ask the orchestrator ("main") for "OK device" /
  "OK restart"; it runs `tools/device-lock.sh grant server`. Then run
  `tools/device-lock.sh take server <what>` in its OWN step and act only if it prints
  "taking device". A container restart counts as device use (hold the lock while restarting).
  When done: `tools/device-lock.sh free server`, then report. Never write /tmp/osrs-device-status
  yourself (the script overwrites it). Grants are single-use; hold the device 15 min at most.
- Deploy = `docker compose up -d --build` in its own command.
- After a container restart or a client reinstall, `server/tools/device.sh login`; if the app
  is not running, start it with `adb shell monkey -p com.jagex.oldscape.android -c
  android.intent.category.LAUNCHER 1`.
- A server subagent's SendMessage goes to "main" (the orchestrator), which relays to the client dev.
- Bookkeeping: keep this file and android-protocol.md current, `scripts/rsprot.sh export`, and
  commit server-owned paths at each working checkpoint with explicit pathspecs
  (`git commit -m "Server: ..." -- <paths>`; the repo-local config sets the author). When your
  context nears compaction, write state here, commit, and hand off.
- Server-owned paths: `server/`, `tools/patch_server.py`, `tools/patch_local_auth.py`,
  `tools/repack.sh` (server options), `native/src/net_trace.*`, `docs/android-protocol.md`,
  `docs/server-handoff.md`. README.md, docs/progress* and tools/progress* belong to the orchestrator.
