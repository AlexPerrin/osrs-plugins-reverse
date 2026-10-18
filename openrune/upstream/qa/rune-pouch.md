# QA: content/rune-pouch

- Branch: `content/rune-pouch` (patch 29, fork PR #11, upstream PR #356)
- Commit: `af57c4ad80496255b90b69b9212b065963407cc8` (base `1be5dfb5f`)
- In-game tree: upstream `main` `c2492318d` + `af57c4ad8` (merges cleanly)
- Date: 2026-10-18
- Procedure: QA.md "Procedure" (`qa/qa.sh`); this was also the dry run of that procedure.

## Verdict: **FAIL**

`af57c4ad8` is the pre-fix commit. What has to change before a re-check:

1. **D1 (accuracy, gameplay-visible):** storing the rune pouch itself from interface 190 must answer
   "Don't be silly." (wiki, Rune pouch, Trivia). Seen in game on this commit: "You can only store runes in
   the rune pouch." Fix it, or list it in Caveats.
2. **Caveats** must list the gaps the accuracy report found and the PR does not declare:
   - death (PvP loss unless locked; PvM gravestone keeps runes);
   - Empty in a bank (into the bank) and bank deposit;
   - Destroy does not drop or clear the contents (the varbits stay set);
   - auto-store (pickup, runecrafting, pickpocketing, ...);
   - divine pouch Revert (and creation);
   - one pouch per player / Glarial's Tomb / Koschei;
   - Empty with too little space stops at the first slot that does not fit (unsourced);
   - the op-to-quantity mapping and Store-X/Withdraw-X not setting the remembered custom amount
     (unsourced, see Evidence 7).
   The current Caveats ("No loadouts", "messages are best guesses") are true but incomplete.
3. **Data sources** says only "Interface, varbits and enum from the cache". The capacity (16,000), the 3/4
   type counts, the NMZ exclusion and the quantity buttons come from the OSRS Wiki (Rune pouch, Divine rune
   pouch pages): name it.
4. **Tests paragraph:** "the in-game checks above were on our own server build" refers to no checks above.
   Replace it with what was checked in game on this branch (QA run on the fixed commit), or drop it.
5. Re-check on the fixed commit **as a members player** (`qa-members.sh`, with the sidecar's members-world
   fix). This run could only send the members ops with `invoke_menu_action`, so its in-game evidence below
   is extra evidence, not a pass on section 3.

## 1. Build and tests: pass

Fresh `git clone --shared` at `af57c4ad8`, Gradle home volume `upstream-pr-gradle-qa`, `--cpus=4
--memory=4g`, `-Xmx2g`, `clean --no-build-cache --no-configuration-cache`, each step its own run.

| Step | Result |
|---|---|
| `clean :or-cache:freshCache :or-cache:mergePluginGamevals` | pass (5m59s) |
| `:or-cache:buildCache` | pass (38s) |
| `spotlessCheck` | pass |
| `assemble` | pass |
| `test` | pass on rerun (1 worker). First run: `:content:quest:test` executor exit 137 (OOM kill in the 4 GB container, 2 workers); a QA setup problem, not the branch |
| `integration` | pass on rerun (1 worker, `.data/game.key` present). First run: `initializationError` (`NoSuchFileException: .data/game.key`, QA ran `generateRsa` too late) and exit 137; QA setup problems, fixed in `qa.sh` |
| all suites | 756 tests, 0 failures/errors; `RunePouchScriptTest` 5/5 |
| boot (main + branch) | "Server ready in 9.78s", no exceptions in the server log during the in-game session |

New behaviour has tests: `RunePouchScriptTest` (5 integration tests: ops index, compact ids, store/withdraw
/empty, 3 types x 16,000 cap, withdraw into a full inventory). The module is new, so every test fails
without the change (the classes don't exist).

## 2. Accuracy: fail

`accuracy/rune-pouch.md` (audited 2026-10-18; it names no commit, but `af57c4ad8` is dated 2026-10-17 and is
the branch tip, so it audits this commit). Every rule names a source. **D1** (store the pouch itself:
"Don't be silly.") is a gameplay-visible message divergence, not fixed, and not in Caveats. The scope gaps
and unsourced rules it lists are not in Caveats (item 2 above).

## 3. In-game verification: not passed (members-player check pending)

Branch server `qa-or-server` (main `c2492318d` + `af57c4ad8`) on 43596, isolated desktop RuneLite 1.13.2
through RSProx (`DESKTOP_TARGET_YAML`), account `desktop` (fresh on this server, staff 2). Upstream accepted
the desktop client as is (headless `desktop_login.py`: login OK, toplevel 161, PASS).

The client showed "Rune pouch (Members)" with only Use/Destroy/Examine (the sidecar served jav_config
`param=3=false`, the desktop members-world flag; since fixed by the orchestrator). The hidden ops were sent
with `invoke_menu_action`; everything else went through the normal widgets. Extra evidence only:

1. Open (op1): interface 190 opens, varp `if1` (261) = 3; "Rune Pouch" title.
2. Store-5 air, Store-All fire, Store-1 air: varbits type/qty `[1:6] [4:20]`; inventory air 94, fire gone.
3. Store-X 7 water: `[2:7]`.
4. 4th type (earth, Store-All): "Your rune pouch has no room for another type of rune.", nothing moved.
5. Store the pouch itself: "You can only store runes in the rune pouch." (**D1**); bronze dagger: the same
   message (non-rune, as intended).
6. Withdraw-1 air: 6 -> 5.
7. Quantity buttons: "Set custom quantity" + 3 -> varbits selected 3 / custom 3, left-click
   "Withdraw-3" (fire 20 -> 17); "5" -> selected 1, left-click withdraws 5; "All" -> selected 2, left-click
   "Store-All". Store-X/Withdraw-X (op5) ask each time and do not set the custom amount (custom stayed 0
   after Store-X 7), so no "Store-7" entry appears; unsourced either way (accuracy row 10).
8. Drag pouch slot 0 onto slot 2: `[1:5] [4:12] [2:50]` -> `[2:50] [4:12] [1:5]`.
9. Use air rune on pouch: all 95 stored (`[1:100]`); use pouch on fire rune: all stored (`[4:20]`).
10. Varrock Teleport (cast via the spellbook op): teleported to 3213,3424; air 100 -> 97 and fire 20 -> 19
    from the pouch, law 10 -> 9 from the inventory.
11. Empty (op4) with a full inventory: "You don't have enough inventory space.", nothing moved; with one
    free slot: water out, then the same message, fire/air stay; with space: all out, varbits 0; Empty on an
    empty pouch: "Your rune pouch is empty."
12. Capacity: 20,000 air used on the pouch -> `[1:16000]`, 4,000 left; again: "Your rune pouch cannot hold
    any more of those runes."
13. Divine rune pouch: Open -> varp 261 = 4, title "Divine Rune Pouch"; four types stored (air, water,
    earth, law), a fifth: "no room for another type".
14. RuneLite Rune Pouch plugin (overlay mode BOTH): rune icons drawn on both pouches in the inventory, from
    the shared varbits (screenshot `rp-shot.png` in the QA scratchpad).
15. Examine (pouch slot and inventory panel): no chat line seen; a plain inventory Examine showed none
    either in the devtools chat history, so not checkable this way.

## 4. PR text review: fail

Checked `pr-texts/rune-pouch.body` and `.upstream.body` (identical apart from the fork PR line).
- True: title/subject `feat:`; module `content/skills/magic/rune-pouch`; enum `rune_compact_ids` = 55
  (gamevals); interface 190 (`interface.rune_pouch`); varp `if1` = slot count (261 = 3/4 seen in game);
  spells cast from the pouch (`MagicRuneManager` reads the same varbits; seen in game); RuneLite's plugin reads
  them (seen); `RunePouchScriptTest` (5, integration); build/test results match this QA run; release note,
  checklist and attribution line present; not stacked (on `main` `1be5dfb5f`, as stated).
- Wrong or missing: Data sources (item 3), Caveats (items 1-2), the "in-game checks above" sentence (item 4).
