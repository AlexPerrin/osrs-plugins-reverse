---
slug: sheep-shearer
track: server
title: 'Sheep Shearer #366: review, RSProx comparison, caveat'
prio: P1
role: server-dev
stage: published
hold: null
deps: []
branch: content/sheep-shearer
commit: 01da94f17e2eb0aeefb0d78c42e69bff2ca94f7a
local_only: false
owner: null
instances: []
upstream_pr: 366
live_commit: 01da94f17e2eb0aeefb0d78c42e69bff2ca94f7a
published_commit: 01da94f17e2eb0aeefb0d78c42e69bff2ca94f7a
created: '2026-10-20T01:42:40Z'
updated: '2026-10-22T00:00:56Z'
---
## Change
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
- 2026-10-21T00:25:19Z migrated from v2 tasks: sheep-shearer-review
- 2026-10-22T00:00:56Z live -> published: observed
