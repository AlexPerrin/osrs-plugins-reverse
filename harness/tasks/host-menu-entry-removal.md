---
id: host-menu-entry-removal
track: client
title: 'Host gap 2: native menu entry removal (No Examine)'
status: shipped
prio: P3
role: client-dev
owner: cd47
instance:
- device
branch: client/host-menu-entry-removal
commit: 6d82e5db9380b7aaada4b2e996b758c97ba6731e
evidence: /home/alex/osrs-plugins-reverse/re/evidence/host-menu-entry-removal
blocked_by: []
unblocks: []
created: '2026-10-20T01:42:43Z'
updated: '2026-10-20T02:05:17Z'
summary: 'Host gap 2 fixed: MenuOpened runs in a menu session and AndroidMenu.applyOrder (presort''s order/removal logic, shared) shortens or reorders the game''s open menu from a plugin''s setMenuEntries.
  Device with hub-no-examine on the new build: Genie + Overgrown cat long-press menu 10 -> 8 entries without Examine, inventory Logs = Use/Drop/Cancel, fountain = Walk here/Cancel, Talk-to tapped in the
  shortened menu ran Talk-to; no exceptions. Host Java only, no network change. Evidence: screenshots, menu-logcat.txt, repack.log (7 telemetry lines). Device restored (check all OK, lock freed).'
---
## Task
Host gap 2: let plugins remove native menu entries (No Examine) through the host's menu hook (docs/client-state.md Next tasks 3).

## Definition of done
- [x] No Examine hub plugin hides Examine entries on the device
- [x] built (host/build.sh + ninja + repack); changed path tried on the device with a screenshot or logcat in re/evidence/host-menu-entry-removal/
- [x] docs/client-state.md open gaps updated

## Log
- 2026-10-20T01:42:43Z created (ready)
- 2026-10-20T01:48:41Z claimed by cd47 (instances device)
- 2026-10-20T01:48:42Z run cd47 started (pid 3795039, headless client-dev)
- 2026-10-20T01:49:23Z set status=ready owner=
- 2026-10-20T01:49:23Z run cd47 restarted with stream-json logging (observability)
- 2026-10-20T01:49:23Z claimed by cd47 (instances device)
- 2026-10-20T01:49:24Z run cd47 started (pid 3800336, headless client-dev)
- 2026-10-20T02:03:43Z device restore check all OK; master's build writes rsprofile.* keys into settings.properties at each login (gen 46 getRSProfiles), so a pre-slot backup taken on an older build differs after a login: pushed the backup back. Worktree builds need re/, base.apk, split_config.arm64_v8a.apk symlinked from the main checkout and MODULUS passed explicitly.
- 2026-10-20T02:03:51Z handover client/host-menu-entry-removal@6d82e5db9: Host gap 2 fixed: MenuOpened runs in a menu session and AndroidMenu.applyOrder (presort's order/removal logic, shared) shortens or reorders the game's open menu from a plugin's setMenuEntries. Device with hub-no-examine on the new build: Genie + Overgrown cat long-press menu 10 -> 8 entries without Examine, inventory Logs = Use/Drop/Cancel, fountain = Walk here/Cancel, Talk-to tapped in the shortened menu ran Talk-to; no exceptions. Host Java only, no network change. Evidence: screenshots, menu-logcat.txt, repack.log (7 telemetry lines). Device restored (check all OK, lock freed).
- 2026-10-20T02:05:00Z run cd47 ended: no result json in the log
- 2026-10-20T02:05:00Z handover-check PASS
- 2026-10-20T02:05:02Z set status=shipped
- 2026-10-20T02:05:02Z shipped: merged into master eeac264, pushed
- 2026-10-20T02:05:17Z run cd47: 94 turns, 15 min, $7.38, 1 classifier denial (APK symlink + repack command; recovered)
