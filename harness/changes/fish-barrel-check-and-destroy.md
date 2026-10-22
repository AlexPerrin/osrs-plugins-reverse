---
slug: fish-barrel-check-and-destroy
track: server
title: 'Fish barrel: Check message box and Destroy'
prio: P1
role: server-dev
stage: published
hold: null
deps: []
branch: fix/fish-barrel-check-and-destroy
commit: 4e2d4562a3eeaeeece1e9bffb6c58c36a963aa81
local_only: false
owner: null
instances:
- desktop
evidence: /home/alex/osrs-plugins-reverse/re/evidence/fish-barrel-check-and-destroy
pr: https://github.com/AlexPerrin/OpenRune-Server/pull/22
ci: https://github.com/AlexPerrin/OpenRune-Server/actions/runs/39367710003
upstream_pr: 388
summary: 'Left: (1) fork PR - tools/fork.sh pr-create was denied by hook (''only the orchestrator does fork pushes and PRs''); orchestrator please open it from docs pr-texts/fish-barrel-check-and-destroy.{title,upstream.body}
  (CI + Formatting already green at 4e2d4562a via workflow_dispatch); (2) client verification of hub-fish-barrel-check after deploy. Done: Check opens the item box (objectbox 193, read by the hub plugin
  at 193:2; the plain message box is 229, found in the desktop check) with ''The barrel is empty.'' / ''The barrel contains:<br>N x Name, ...'', no game messages; Destroy (op 5) shows the confirmation (empty/filled
  notes from wiki/cache) and removes the barrel, so the tester''s slot-15 barrel can go once deployed; Fill on a full barrel sends the wiki''s GAMEMESSAGE ''The barrel is full. It may be emptied at a bank.''
  (a game message per wiki and plugin, not a Check box). Desktop-verified on the branch server; local gate 769 tests/79 integration pass. No RSProx recording of the barrel exists. Spec and STATE on origin
  docs 002d1bc5a; fork docs has diverged from origin docs (fork.sh push docs rejected non-ff).'
gated_commit: 4e2d4562a3eeaeeece1e9bffb6c58c36a963aa81
live_commit: 4e2d4562a3eeaeeece1e9bffb6c58c36a963aa81
published_commit: 4e2d4562a3eeaeeece1e9bffb6c58c36a963aa81
cost:
  runs: 1
  turns: 24
  minutes: 19.5
  usd: 7.54
created: '2026-10-20T01:42:40Z'
updated: '2026-10-22T00:38:23Z'
---
## Change
Fish barrel Check sends GAMEMESSAGE lines instead of the chatbox message box (interface 193 comp 2: 'The barrel is empty.' / 'The barrel contains: 3 x Raw shrimps' / 'The barrel is full. It may be emptied at a bank.'); Destroy (op 5) does nothing (FishBarrelScript dispatch `else -> {}`). A spawned barrel sits in the tester's slot 15 until Destroy works. Unblocks client: Fish Barrel hub plugin (gen 44).

## Definition of done
- [x] Check opens the chatbox message box (interface 193 comp 2) with the three wiki texts, no GAMEMESSAGE lines
- [x] Destroy (op 5) shows the destroy confirmation and removes the barrel; the tester's slot-15 barrel can be destroyed
- [x] Unblocks client task hub-fish-barrel-check (the client dev verifies it there after the deploy)
- [x] accuracy/fish-barrel-check-and-destroy.md on the fork's docs branch (wiki/cache baseline per rule, RSProx comparison recorded)
- [x] fork PR open with green CI at the handed-over commit (#22, CI 39367710003)
- [x] one change only, branch off upstream main (no stacking)

## Log
- 2026-10-21T00:25:19Z migrated from v2 tasks: fish-barrel-check-and-destroy
- 2026-10-22T00:01:06Z run sq31 started (server-qa, stage gate, instances desktop)
- 2026-10-22T00:28:08Z gate PASS claimed: upstream gate PASS at 4e2d4562a: record qa/fish-barrel-check-and-destroy.md on docs 7e2271897; CI+Formatting green at the commit, accuracy re-checked at wiki/cache/plugin sources, all PR claims seen in game on main+branch (the record on docs is what counts)
- 2026-10-22T00:28:30Z live -> gated: observed
- 2026-10-22T00:28:37Z run sq31 ended: 24 turns, 19.5 min, $7.54, exit ok
- 2026-10-22T00:37:35Z approval fish-barrel-check-and-destroy-open-pr-4e2d456 executed (open-pr): https://github.com/OpenRune/OpenRune-Server/pull/388
- 2026-10-22T00:38:23Z gated -> published: observed
