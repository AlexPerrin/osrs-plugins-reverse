# Fork content branches (for discussion issue #353)

Prepared 2026-10-18. One branch per content area of OpenRune/OpenRune-Server#353, in the local clone
`re/upstream-pr/or` (git-ignored), off upstream `main` [`1be5dfb5f`](https://github.com/OpenRune/OpenRune-Server/commit/1be5dfb5f1a2efa314b93d6c1e2ae3dac6db41d1),
which is also the fork's `main`. Each branch is the area's patches from this directory applied with `git am -3`
in README order; commits keep their authorship and message, with only the subject reworded to `feat:`/`fix:`.
Nothing has been pushed or opened.

PR texts: `fork-pr-texts/<slug>.title` / `.body` (slug = branch name without `content/`). To open one later
(PR inside the fork, base = fork `main`):

```
git -C re/upstream-pr/or push fork content/<slug>
gh pr create -R AlexPerrin/OpenRune-Server --base main --head content/<slug> \
  --title "$(cat openrune/upstream/fork-pr-texts/<slug>.title)" --body-file openrune/upstream/fork-pr-texts/<slug>.body
```

| Branch (commit) | Patches | Tests (cache build, spotless, assemble/test/integration, boot) | PR |
|---|---|---|---|
| `content/quests-sheep-romeo` `f7044ec81` | 12 | cache build pass, `spotlessCheck` pass, `assemble test integration` pass, boot: Server ready in 8.458180081s | _(PR URL)_ |
| `content/doric` `dd19f2239` | 12, 21, 22, 64 | cache build pass, `spotlessCheck` pass, `assemble test integration` pass, boot: Server ready in 7.160229819s | _(PR URL)_ |
| `content/witchs-potion` `4fb55995e` | 12, 65 | cache build pass, `spotlessCheck` **FAIL**, `assemble test integration` pass, boot: Server ready in 7.120464066s | _(PR URL)_ |
| `content/goblin-diplomacy` `270b5a0e3` | 12, 66 | cache build pass, `spotlessCheck` pass, `assemble test integration` pass, boot: Server ready in 7.466380557s | _(PR URL)_ |
| `content/vampyre-slayer` `fa6e37a83` | 12, 21, 22, 64, 66, 67 | cache build pass, `spotlessCheck` pass, `assemble test integration` pass, boot: Server ready in 8.639919005s | _(PR URL)_ |
| `content/treasure-trails` `544a89fd1` | 21, 22, 23, 24, 28, 32, 33, 34 | cache build pass, `spotlessCheck` pass, `assemble test integration` pass, boot: Server ready in 6.945873432s | _(PR URL)_ |
| `content/farming` `c9c319879` | 19 | cache build pass, `spotlessCheck` pass, `assemble test integration` pass, boot: Server ready in 7.796655669s | _(PR URL)_ |
| `content/hunter` `6310b11ef` | 15, 20, 35, 39, 40, 41, 42 | cache build pass, `spotlessCheck` **FAIL**, `assemble test integration` pass, boot: Server ready in 7.171814717s | _(PR URL)_ |
| `content/fairy-rings` `0ffe5b572` | 26 | cache build pass, `spotlessCheck` pass, `assemble test integration` pass, boot: Server ready in 7.185355388s | _(PR URL)_ |
| `content/grand-exchange` `8c7cb1ecd` | 48, 50, 51, 52 | cache build pass, `spotlessCheck` **FAIL**, `assemble test integration` pass, boot: Server ready in 6.722064831s | _(PR URL)_ |
| `content/diaries` `8e295489f` | 47, 49, 53 | cache build pass, `spotlessCheck` **FAIL**, `assemble test integration` pass, boot: Server ready in 7.093000459s | _(PR URL)_ |
| `content/jewellery` `0d63b1a22` | 44, 45 | cache build pass, `spotlessCheck` pass, `assemble test integration` pass, boot: Server ready in 7.172812007s | _(PR URL)_ |
| `content/cannon` `0114f7334` | 13 | cache build pass, `spotlessCheck` pass, `assemble test integration` pass, boot: Server ready in 6.952898445s | _(PR URL)_ |
| `content/genie` `90e776e18` | 16 | cache build pass, `spotlessCheck` pass, `assemble test integration` pass, boot: Server ready in 7.268255836s | _(PR URL)_ |
| `content/rune-pouch` `78c936dc4` | 29 | cache build pass, `spotlessCheck` pass, `assemble test integration` pass, boot: Server ready in 7.054562582s | _(PR URL)_ |
| `content/plank-sack` `fc05c221d` | 30 | cache build pass, `spotlessCheck` pass, `assemble test integration` pass, boot: Server ready in 7.071947733s | _(PR URL)_ |

## Stacking

- `doric` = 12 + 21 + 22 + 64: Doric's Quest uses `LocOpOverrides` from the Treasure Trails patch (21), which needs the
  music tab (22), and the quest tests use 12's `QuestTalkFixture`. The README only listed 12 and 21 for it.
- `witchs-potion` = 12 + 65: its test uses `QuestTalkFixture` (the README listed no dependency).
- `goblin-diplomacy` = 12 + 66.
- `vampyre-slayer` = the `doric` branch + 66 + 67.
- The quest branches share the same 12 commit (`f7044ec81`), and `vampyre-slayer` shares `doric`'s commits.
- `treasure-trails` carries 22 (music tab) and 23 (drawers/chests), which overlap the separate batch-2 branch
  `fix/23-openable-drawers-chests` (that one leaves the Draynor wardrobe out). 25 (Ice Mountain music area) is not
  included (wiki polygon; not a build dependency).
- `hunter` includes bird houses (20), which need the hunter module from implings (15); one branch for all seven.
- Pirate's Treasure (68): no patch in `openrune/upstream/` yet, so no branch.

## Conflict resolutions (inside the patch commits)

- `witchs-potion` (65) and `vampyre-slayer` (67): quest pack `gamevals.toml`, only the patch's own lines kept (the
  patch context had lines of quests not on that branch).
- `diaries` (49): `CookingEvents.kt`, the diary hook is added after main's own "You successfully cook the ..."
  message; the patch context had patch 09's reworded spam line, which is not on this branch.

## How it was tested

Docker `eclipse-temurin:21`, upstream's `./gradlew`, `--cpus=4 --memory=4g`, `--max-workers=2`, `-Xmx2g`, one
container at a time. `:or-cache:freshCache :or-cache:mergePluginGamevals` once on main
(`JAVA_TOOL_OPTIONS=-Xmx2600m`), kept aside; per branch: restore that cache, `:or-cache:buildCache
:or-cache:mergePluginGamevals`, `spotlessCheck`, `assemble`, `test` and `integration` (separate invocations), then
`run` until "Server ready in". A single `assemble test integration` invocation was OOM-killed by the 4 GB container
limit on three quest branches (Gradle daemon gone, no test failure); split into three invocations they pass.
Nothing was checked in game from these branches.

## Notes

- `spotlessCheck` fails on four branches, formatting only, from the patches themselves (`spotlessApply` fixes each;
  compile, tests and boot pass): `witchs-potion` (65: trailing blank line in `WitchsPotion.kt`), `hunter` (35: blank
  line before a closing brace in `HunterTrapScript.kt`), `grand-exchange` (50/51: two blank lines before KDoc, import
  order in `GrandExchangeScript.kt`), `diaries` (49/53: hook imports out of order in 17 files of 13 modules). The
  branches keep the patches as they are and the PR texts say so; fixing the patches and re-applying would make them
  pass. (The README's "spotless-checked in our tree" was against our tree's ratchet base, not upstream main.)
- `quests-sheep-romeo` was tested with one `assemble test integration` invocation (it passed before the OOMs showed up);
  the others with three.
- `grand-exchange`: works in game only with #352's compiler fix and the RSProt bump; the PR text says so.
- Debug chat commands in the patches (content-scoped, like `::star`): `::growpatches` (farming), `::randomevent`
  (genie), `::birdhouses` and `::rumour` (hunter), `::clue` (treasure trails). Not our QuestTestCommand; listed in the
  PR texts.
- `fairy-rings` closes the travel log's mobile search input (meslayer mode 28) on teleport; generic client
  behaviour, but only seen on the mobile client.
