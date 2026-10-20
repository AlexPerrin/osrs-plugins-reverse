---
id: fish-barrel-check-and-destroy
track: server
title: 'Fish barrel: Check message box and Destroy'
status: ready
prio: P1
role: server-dev
owner: null
instance: []
branch: null
commit: null
blocked_by: []
unblocks:
- hub-fish-barrel-check
created: '2026-10-20T01:42:40Z'
updated: '2026-10-20T01:42:58Z'
---
## Task
Fish barrel Check sends GAMEMESSAGE lines instead of the chatbox message box (interface 193 comp 2: 'The barrel is empty.' / 'The barrel contains: 3 x Raw shrimps' / 'The barrel is full. It may be emptied at a bank.'); Destroy (op 5) does nothing (FishBarrelScript dispatch `else -> {}`). A spawned barrel sits in the tester's slot 15 until Destroy works. Unblocks client: Fish Barrel hub plugin (gen 44).

## Definition of done
- [ ] Check opens the chatbox message box (interface 193 comp 2) with the three wiki texts, no GAMEMESSAGE lines
- [ ] Destroy (op 5) shows the destroy confirmation and removes the barrel; the tester's slot-15 barrel can be destroyed
- [ ] Unblocks client task hub-fish-barrel-check, verified by the client dev
- [ ] accuracy/fish-barrel-check-and-destroy.md on the fork's docs branch (wiki/cache baseline per rule, RSProx comparison recorded)
- [ ] fork PR open with green CI at the handed-over commit
- [ ] one change only, branch off upstream main (no stacking)

## Log
- 2026-10-20T01:42:40Z created (ready)
- 2026-10-20T01:42:58Z unblocks hub-fish-barrel-check
