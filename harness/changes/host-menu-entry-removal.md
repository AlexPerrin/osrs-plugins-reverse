---
slug: host-menu-entry-removal
track: client
title: 'Host gap 2: native menu entry removal (No Examine)'
prio: P3
role: client-dev
stage: live
hold: null
deps: []
branch: client/host-menu-entry-removal
commit: 6d82e5db9380b7aaada4b2e996b758c97ba6731e
local_only: false
owner: null
instances: []
evidence: /home/alex/osrs-plugins-reverse/re/evidence/host-menu-entry-removal
summary: 'Host gap 2 fixed: MenuOpened runs in a menu session and AndroidMenu.applyOrder (presort''s order/removal logic, shared) shortens or reorders the game''s open menu from a plugin''s setMenuEntries.
  Device with hub-no-examine on the new build: Genie + Overgrown cat long-press menu 10 -> 8 entries without Examine, inventory Logs = Use/Drop/Cancel, fountain = Walk here/Cancel, Talk-to tapped in the
  shortened menu ran Talk-to; no exceptions. Host Java only, no network change. Evidence: screenshots, menu-logcat.txt, repack.log (7 telemetry lines). Device restored (check all OK, lock freed).'
live_commit: 6d82e5db9380b7aaada4b2e996b758c97ba6731e
created: '2026-10-20T01:42:43Z'
updated: '2026-10-21T00:25:19Z'
---
## Change
Host gap 2: let plugins remove native menu entries (No Examine) through the host's menu hook (docs/client-state.md Next tasks 3).

## Definition of done
- [x] No Examine hub plugin hides Examine entries on the device
- [x] built (host/build.sh + ninja + repack); changed path tried on the device with a screenshot or logcat in re/evidence/host-menu-entry-removal/
- [x] docs/client-state.md open gaps updated

## Log
- 2026-10-21T00:25:19Z migrated from v2 tasks: host-menu-entry-removal
