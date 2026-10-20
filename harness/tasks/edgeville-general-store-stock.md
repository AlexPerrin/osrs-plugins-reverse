---
id: edgeville-general-store-stock
track: orch
title: 'Upstream #371: Edgeville general store stock'
status: done
prio: P1
role: orchestrator
owner: null
instance: []
branch: fix/edgeville-general-store-stock
commit: a695b985f
pr: https://github.com/OpenRune/OpenRune-Server/pull/372
blocked_by: []
unblocks: []
created: '2026-10-20T01:42:43Z'
updated: '2026-10-20T03:33:17Z'
---
## Task
Rebuild upstream's truncated generalshop2.toml (size 0 -> crash on Trade), data only (sd24, sq23). Fork PR #19 fix/edgeville-general-store-stock @ ca527418a -> upstream #371 (opened 2026-10-19); internal + upstream PASS (fork docs qa/edgeville-general-store-stock.md). #351 (fix/55, draft since 2026-10-18) depends on it and leaves draft after #371 merges; fix/55 is out of integration until then.

## Definition of done
- [ ] #371 merged upstream
- [ ] #351 (fix/55) taken out of draft afterwards with the user's OK; fix/55 back in integration (repin-2)

## Log
- 2026-10-20T01:42:43Z created (waiting-user)
- 2026-10-20T02:11:42Z set status=in-progress owner=sq25
- 2026-10-20T02:11:42Z old harness (session 73): sd24 follow-up a695b985f (general stores without a handler, fix/edgeville-general-store-stock); sq25 gate running on re/agents/sq25/r-store
- 2026-10-20T02:12:36Z Recordings 1619/3216: Lumbridge->generalshop1, Varrock->generalshop2 (stock files swapped, wiring mostly right). User approved: #371 = single data+handlers PR, close #351; #371 stays draft until ready. sd24 d3cc4faf4 (4 tomls + csv + Varrock wiring) + a695b985f (handlers Al Kharid, Karamja, Zanaris, Nardah, Miscellania), local gate + in game pass; NOT pushed (would update draft #371). sq25 upstream gate running (local). Texts re/agents/sd24/docs aa924079b. Members' stores (~29) proposed as a separate follow-up PR from RSProx (user answer pending).
- 2026-10-20T02:46:55Z sq25 upstream gate PASS at a695b985f (local gate on main 829b9477d merge: test 764/0, integration 79/0, boot clean; RSProx 1619/3216/424 + wiki + #341; Trade checked in game at all 10 stores). Record 8c59858aa on 238fb7bff in re/agents/sq25/docs, NOT pushed. Spec caveat about Al Kharid having no handler is stale (spec only). Next, user OK: ff push a695b985f onto #371 branch (stays draft), #371 title/body via comment rules, #351 close comment.
- 2026-10-20T02:46:55Z set status=gated branch=fix/edgeville-general-store-stock commit=a695b985f
- 2026-10-20T02:55:23Z User OK: pushed a695b985f to #371 (still draft), comment https://github.com/OpenRune/OpenRune-Server/pull/371#issuecomment-6282208379; #351 closed with comment. The #371 title ('restore the Edgeville General Store stock') is now stale; fix it before ready.
- 2026-10-20T03:00:41Z User OK: #371 retitled 'fix: general store stock on the official inventories, and handlers for the stores that had none' (title only, still draft).
- 2026-10-20T03:05:31Z User: move the scope change into a new PR. QA records 8c59858aa pushed to fork docs. #371 closed (comment), new draft PR #372 opened from the same branch at a695b985f (https://github.com/OpenRune/OpenRune-Server/pull/372), title 'fix: general store stock on the official inventories, and handlers for the stores that had none', body replaces #351 and #371; link comment on #371. Draft until the user says ready.
- 2026-10-20T03:05:31Z set pr=https://github.com/OpenRune/OpenRune-Server/pull/372
- 2026-10-20T03:06:44Z User OK: #372 marked ready for review.
- 2026-10-20T03:13:49Z approval requested: harness/approvals/edgeville-general-store-stock.md
- 2026-10-20T03:33:17Z User 2026-10-20: stale approval cleared; the upstream action was already done with the user's OK.
- 2026-10-20T03:33:17Z done
