# Shipped during harness v2

v2 ran from commit 72d55d2 (2026-10-20 01:45 UTC) through the orchestrator session a450a085 (until the v3 hand-off).
Sources: the task store (32 tasks reached done, live or shipped in the window), git history (master and the fork),
and `gh pr list -R OpenRune/OpenRune-Server --author AlexPerrin`. Progress definitions: client = RuneLite
compatibility on mobile, server = parity with the official OSRS server (docs/progress.md).

## Server: live on the test server

Deployed 2026-10-20 04:44 UTC (integration e5b18a87b, image integration-e5b18a8), re-pinned on upstream main
829b9477d:

| Feature | Branch | Task | Upstream |
|---|---|---|---|
| Re-pin onto current upstream main (brings upstream's Slayer #312, Waterfall #297, Doric's Quest #334, Knight's Sword #333, Ides of Milk #315, our idle logout #348) | integration | repin-2 | n/a |
| Stile: climb from the tile outside the stile, in line with it (17 crossings match RSProx 1718) | fix/stile-approach | fix-stile-approach | fork PR #21; upstream gate next |
| Fish barrel: Check opens the item box, Destroy works | fix/fish-barrel-check-and-destroy | fish-barrel-check-and-destroy | fork PR #22 |
| Rune pouch: "Don't be silly." when storing the pouch in itself | fix/rune-pouch-accuracy | (re-pin) | #370, in review |
| General stores: official stock and handlers for the stores that had none | fix/edgeville-general-store-stock | edgeville-general-store-stock | #372, ready for review |

Shipped, in train int-20261020-1 (integration e27ff4d08, gate running at hand-off), not yet live:

| Feature | Branch | Task | Notes |
|---|---|---|---|
| Clue scroll Check names the tier ("...on this hard clue scroll.") | local/treasure-trails | clue-check-steps-tier | local-only; RSProx 91/1034/1106/3931 |
| Ground items: OSRS despawn timers (tradeable 100/300 ticks, untradeable 300 never public, clue 6100) and OBJ_ADD timer/ownership fields; untradeable items read from the cache's stockmarket flag (upstream bug) | fix/ground-item-despawn-times | ground-item-despawn-times | fork PR #23; supersedes fix/76-obj-add-timers (dropped); follow-up despawn-copy-owner-id |

## Client: on master (RuneLite compatibility on mobile)

| Feature | Task | Result |
|---|---|---|
| The user's own RuneLite plugins: status table and a batch port | hub-batch-next | 22 of 25 installed hub plugins start on the device (Quest Helper, Tile Packs, One Tick Flick, Visual Ticks, Bank Tag Layouts, Motherlode, Tick Fishing, BA x2, GotR x2, Forestry, Stealing Artefacts, kebbit tracking, Perilous Moons, Trouble Brewing, Gnome Restaurant, drift net, Sailing, Barracuda Trials, Camera Smoothing); camera setters implemented so Camera Smoothing works; 11 server content tasks filed for plugins whose content the server lacks |
| Fish Barrel plugin: Check path verified on the device; the tester's spare barrel destroyed | hub-fish-barrel-check | overlay shows the stored count after Check |
| Native menu entry removal (No Examine) | host-menu-entry-removal | live |
| Code-scanning fixes (snprintf bounds in native trace, local-auth patch patterns) | PR #1 (agentic-autofix) | merged 7aff4a459 |
| Network QA with a GE digest fetch | netqa-ge-digest | PASS: digest only to the local sidecar, no Jagex traffic (sidecar 404 retry filed: sidecar-ge-digest) |
| Client docs trimmed to current state | client-docs-trim | live |

Client coverage at hand-off (tools/progress.py): RuneLite API 661/866 (76%), core plugins on Android 89/90,
Plugin Hub plugins 84/109, mobile packets mapped 240/253.

## Upstream (OpenRune/OpenRune-Server)

| PR | What happened in v2 |
|---|---|
| #362 world map stone choice | gated text posted, retitled, marked ready |
| #366 Sheep Shearer | fast-forwarded to the RSProx-matched quest, description replaced, marked ready |
| #367 Goblin Diplomacy | fast-forwarded to the quest only (ladder split out), retitled, gated text, marked ready after green CI |
| #372 general stores | opened (replaces #351 and #371), marked ready |
| #373 Goblin Village lookout ladder | opened, ready |
| #351, #371 | closed (superseded by #372) |
| #354 cannon, #355 Genie | gates re-run at hand-off (cannon PR text fixed; Genie spec written + one more accuracy fix, acc/genie 3ccb86bd8); the user approved leaving draft after PASS |

Gates passed in v2: Goblin Village lookout ladder, Goblin Diplomacy (quest only), and the #362, #366 and #372
updates. Decided: no stacked PRs, no PR order, RSProx item N/A on non-content PRs, rsprot bump waits for the Grand
Exchange gate, op-overrides dropped from upstream (kept local).

## Harness and tooling

- Publish hook rewritten to resolve real push targets (fork vs OpenRune) instead of matching words; shared shell
  parser; fixes for in-command variables, `$(...)`, `--help`, heredoc text, worktrees made in the same command.
- Hand-over check: docs-only client changes, QA tasks, spec name variants, `local/*` branches; QA gate PASS goes to
  gated (approval kept).
- Reap: skips approvals already done upstream. Ship: four bugs fixed; server ships now enqueue.
- `live` status (client checks unblock only after a deploy) and `migrate-live`.
- Integration pipeline (docs/integration-pipeline.md) phases 0-1: deny rules for deploy/image/ship, the queue, the
  integration lock, trains, commit pins (integration.lock on the fork). First train run end to end.
- Locks: `reclaim` and `revoke` for the orchestrator; `LOCK_DIR` for tests.
- Agent rules in every brief and role file: Ending (exit when done), locks only while in use, per-agent scratch
  dirs, no screenshots unless the user asks.
- Harness tests: 0 -> 60. Progress report (tools/harness/progress_report.py -> docs/progress.md).
- Server history archived to the fork's docs branch (209 files out of the client repo); fork default branch set to
  integration; Docker and Gradle volume cleanup.
- v2 review (re/evidence/harness-v2-review/) and the v3 handoff (docs/harness-v3-handoff.md).
