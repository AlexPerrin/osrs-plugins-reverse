---
id: content-forestry-events
track: server
title: Forestry woodcutting events (Rising Roots, Struggling Sapling, ...)
status: backlog
prio: P3
role: server-dev
owner: null
instance: []
branch: null
commit: null
blocked_by: []
unblocks: []
created: '2026-10-20T04:57:23Z'
updated: '2026-10-20T04:57:23Z'
---
## Task
Found by client task hub-batch-next (the user's own Plugin Hub list, orchestrator 2026-10-20): the plugin builds, passes netcheck/stubcheck and starts on the device, but OpenRune has no content for it to read, so it can't be exercised. Survey 2026-10-20 of re/upstream-pr/or (docs tree e71437e and local integration e5b18a8): content/skills/woodcutting has plain Woodcutting.kt only. Wiki: Forestry. Check upstream again first, spec with accuracy-check (OSRS Wiki and cache are the baseline), one PR per change, no stacking. Client re-test after it is live: hub forestrycc tracks events (also via the Forestry CC clan chat)

## Definition of done
- [ ] upstream re-checked for an existing implementation or PR before starting
- [ ] feature works end to end on the test server as on the wiki
- [ ] accuracy spec on the fork's docs branch (wiki/cache baseline per rule)
- [ ] fork PR open with green CI at the handed-over commit

## Log
- 2026-10-20T04:57:23Z created (backlog)
