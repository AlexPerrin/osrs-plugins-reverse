---
id: fish-barrel-check-and-destroy
track: server
title: 'Fish barrel: Check message box and Destroy'
status: live
prio: P1
role: server-dev
owner: sd26
instance:
- desktop
branch: fix/fish-barrel-check-and-destroy
commit: 4e2d4562a3eeaeeece1e9bffb6c58c36a963aa81
pr: https://github.com/AlexPerrin/OpenRune-Server/pull/22
ci: https://github.com/AlexPerrin/OpenRune-Server/actions/runs/39367710003
evidence: /home/alex/osrs-plugins-reverse/re/evidence/fish-barrel-check-and-destroy
blocked_by: []
unblocks:
- hub-fish-barrel-check
created: '2026-10-20T01:42:40Z'
updated: '2026-10-20T05:29:22Z'
summary: 'Left: (1) fork PR - tools/fork.sh pr-create was denied by hook (''only the orchestrator does fork pushes and PRs''); orchestrator please open it from docs pr-texts/fish-barrel-check-and-destroy.{title,upstream.body}
  (CI + Formatting already green at 4e2d4562a via workflow_dispatch); (2) client verification of hub-fish-barrel-check after deploy. Done: Check opens the item box (objectbox 193, read by the hub plugin
  at 193:2; the plain message box is 229, found in the desktop check) with ''The barrel is empty.'' / ''The barrel contains:<br>N x Name, ...'', no game messages; Destroy (op 5) shows the confirmation (empty/filled
  notes from wiki/cache) and removes the barrel, so the tester''s slot-15 barrel can go once deployed; Fill on a full barrel sends the wiki''s GAMEMESSAGE ''The barrel is full. It may be emptied at a bank.''
  (a game message per wiki and plugin, not a Check box). Desktop-verified on the branch server; local gate 769 tests/79 integration pass. No RSProx recording of the barrel exists. Spec and STATE on origin
  docs 002d1bc5a; fork docs has diverged from origin docs (fork.sh push docs rejected non-ff).'
integration: e5b18a87b
---
## Task
Fish barrel Check sends GAMEMESSAGE lines instead of the chatbox message box (interface 193 comp 2: 'The barrel is empty.' / 'The barrel contains: 3 x Raw shrimps' / 'The barrel is full. It may be emptied at a bank.'); Destroy (op 5) does nothing (FishBarrelScript dispatch `else -> {}`). A spawned barrel sits in the tester's slot 15 until Destroy works. Unblocks client: Fish Barrel hub plugin (gen 44).

## Definition of done
- [x] Check opens the chatbox message box (interface 193 comp 2) with the three wiki texts, no GAMEMESSAGE lines
- [x] Destroy (op 5) shows the destroy confirmation and removes the barrel; the tester's slot-15 barrel can be destroyed
- [x] Unblocks client task hub-fish-barrel-check (the client dev verifies it there after the deploy)
- [x] accuracy/fish-barrel-check-and-destroy.md on the fork's docs branch (wiki/cache baseline per rule, RSProx comparison recorded)
- [x] fork PR open with green CI at the handed-over commit (#22, CI 39367710003)
- [x] one change only, branch off upstream main (no stacking)

## Log
- 2026-10-20T01:42:40Z created (ready)
- 2026-10-20T01:42:58Z unblocks hub-fish-barrel-check
- 2026-10-20T03:16:26Z claimed by sd26 (instances desktop)
- 2026-10-20T03:26:56Z set status=specced
- 2026-10-20T03:26:56Z spec on docs (accuracy/fish-barrel-check-and-destroy.md). No RSProx recording of the barrel (rsprox.net OBJ/TEXT/MESSAGE_GAME search 0). Note: 'The barrel is full...' is a GAMEMESSAGE per wiki and the hub plugin (onChatMessage), not a Check message box; implemented on Fill when full. osrs-devtools(-desktop) MCP failed to connect at session start.
- 2026-10-20T03:27:53Z denied: tools/fork.sh pr-create fix/fish-barrel-check-and-destroy (PreToolUse hook: 'Only the orchestrator does fork pushes and PRs'); branch pushed to the fork at 2909b6e91 (fork.sh push passed). Spec pushed to origin docs (ae69513eb); fork docs has diverged from origin docs (QA commits 8c59858aa..), so fork.sh push docs was rejected non-fast-forward. Needs the orchestrator: open the fork PR (title/body on docs pr-texts/fish-barrel-check-and-destroy.*) and reconcile docs.
- 2026-10-20T03:45:36Z denied: git push origin / fork.sh push of fix/fish-barrel-check-and-destroy at 4e2d4562a (hook: 'Only the orchestrator does pushes'). Live desktop check found the official Check box is interface 193 = objectbox (item box), not messagebox 229; second commit 4e2d4562a switches to objbox. Needs the orchestrator to push 4e2d4562a (fast-forward over 2909b6e91) and open the fork PR.
- 2026-10-20T04:20:18Z handover fix/fish-barrel-check-and-destroy@4e2d4562a: Left: (1) fork PR - tools/fork.sh pr-create was denied by hook ('only the orchestrator does fork pushes and PRs'); orchestrator please open it from docs pr-texts/fish-barrel-check-and-destroy.{title,upstream.body} (CI + Formatting already green at 4e2d4562a via workflow_dispatch); (2) client verification of hub-fish-barrel-check after deploy. Done: Check opens the item box (objectbox 193, read by the hub plugin at 193:2; the plain message box is 229, found in the desktop check) with 'The barrel is empty.' / 'The barrel contains:<br>N x Name, ...', no game messages; Destroy (op 5) shows the confirmation (empty/filled notes from wiki/cache) and removes the barrel, so the tester's slot-15 barrel can go once deployed; Fill on a full barrel sends the wiki's GAMEMESSAGE 'The barrel is full. It may be emptied at a bank.' (a game message per wiki and plugin, not a Check box). Desktop-verified on the branch server; local gate 769 tests/79 integration pass. No RSProx recording of the barrel exists. Spec and STATE on origin docs 002d1bc5a; fork docs has diverged from origin docs (fork.sh push docs rejected non-ff).
- 2026-10-20T04:21:13Z set pr=https://github.com/AlexPerrin/OpenRune-Server/pull/22
- 2026-10-20T04:21:13Z Orchestrator: fork PR #22 opened (sd26's pr-create hit the old hook); sd26's docs ae69513eb/197b83940/002d1bc5a replayed onto fork docs as 28f2c5227.
- 2026-10-20T04:21:16Z handover-check FAIL (1 items; see the check output)
- 2026-10-20T04:21:26Z handover-check PASS
- 2026-10-20T04:25:12Z set status=shipped integration=e5b18a87b
- 2026-10-20T04:25:12Z shipped: in integration e5b18a87b (pushed with a lease over a07ec2d8f); gate + deploy pending
- 2026-10-20T04:44:42Z Live 2026-10-20 04:44 UTC (integration e5b18a87b, image integration-e5b18a8).
- 2026-10-20T05:29:22Z migrate-live: shipped -> live (deployed: Live 2026-10-20 04:44 UTC (integration e5b18a87b, image integration-e5b18a8))
