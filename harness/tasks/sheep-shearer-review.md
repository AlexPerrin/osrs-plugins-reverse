---
id: sheep-shearer-review
track: server
title: 'Sheep Shearer #366: review, RSProx comparison, caveat'
status: done
prio: P1
role: server-dev
owner: null
instance: []
branch: content/sheep-shearer
commit: 01da94f17
blocked_by: []
unblocks: []
created: '2026-10-20T01:42:40Z'
updated: '2026-10-20T03:33:17Z'
---
## Task
#366 (content/sheep-shearer, fork PR #16) maintainer review 2026-10-19: stile jump from too far away (fixed separately in fix-stile-approach), remove SheepShearerInteractionTest, PR text in the new template with The Thing caveat (no run-away, generic shearing); stays draft (user's call). Both gates PASS at 069c15fde (upstream queue row says 19aefd90a; fork docs qa/sheep-shearer.md). Still needed: the RSProx comparison (recordings 1718 rev 227 Leagues V whole quest ticks 7184-7744, and 1619-1622 rev 225 shearing only; transcripts re/ref/rsprox/txt/, notes re/ref/rsprox/notes/sheep-shearer.md), then the #366 update after the user's OK: fast-forward push 069c15fde onto content/quests-sheep-romeo, the review comment (stile fixed separately), ready vs draft. Companion sq24. CI first run on 069c15fde: gh run list -R AlexPerrin/OpenRune-Server --branch content/sheep-shearer.

## Definition of done
- [ ] SheepShearerInteractionTest removed; PR text in the upstream template with The Thing caveat; no stile change in this branch
- [ ] RSProx comparison written into accuracy/sheep-shearer.md (recordings 1718 and 1619-1622) with each divergence as a caveat
- [ ] upstream gate re-run by sq24 PASS at the final commit (qa/sheep-shearer.md on fork docs)
- [ ] fast-forward push onto content/quests-sheep-romeo (#366), review comment and ready/draft only after the user's OK
- [ ] accuracy/sheep-shearer-review.md on the fork's docs branch (wiki/cache baseline per rule, RSProx comparison recorded)
- [ ] fork PR open with green CI at the handed-over commit
- [ ] one change only, branch off upstream main (no stacking)

## Log
- 2026-10-20T01:42:40Z created (ready)
- 2026-10-20T02:39:48Z sq24 upstream gate PASS at content/sheep-shearer 01da94f17 (CI 39353944420/39353946179 green; all RSProx-1718 FAIL items fixed and checked in game). Record in re/agents/sq24/docs branch docs-publish 60f5e23ea (on fork docs 9a451125c), NOT pushed. Gameval 65449 clashes with #368's witchs_potion_progress. Next: user OK for the ff push onto #366 (content/quests-sheep-romeo) + comment + ready vs draft.
- 2026-10-20T02:39:48Z set status=gated branch=content/sheep-shearer commit=01da94f17
- 2026-10-20T02:55:23Z User OK: pushed 01da94f17 onto content/quests-sheep-romeo (#366), comment https://github.com/OpenRune/OpenRune-Server/pull/366#issuecomment-6282247227, marked ready for review.
- 2026-10-20T03:13:49Z approval requested: harness/approvals/sheep-shearer-review.md
- 2026-10-20T03:22:59Z User OK: #366 description replaced with the gated text (sd23 docs 65cc94f05 pr-texts/sheep-shearer.upstream.body, pr-text-review true per sq24 at 01da94f17); title kept.
- 2026-10-20T03:33:17Z User 2026-10-20: stale approval cleared; the upstream action was already done with the user's OK.
- 2026-10-20T03:33:17Z done
