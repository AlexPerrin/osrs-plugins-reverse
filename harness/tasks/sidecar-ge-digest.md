---
id: sidecar-ge-digest
track: client
title: 'Sidecar: serve the GE digest (404 now, client retries every 10 s)'
status: ready
prio: P3
role: client-dev
owner: null
instance: []
branch: null
commit: null
blocked_by: []
unblocks: []
created: '2026-10-20T03:34:37Z'
updated: '2026-10-20T03:34:37Z'
network: true
---
## Task
Sidecar: serve the GE digest (404 now, client retries every 10 s)

## Definition of done
- [ ] the sidecar (10.0.2.2:8080) answers GET /m=itemdb_oldschool/g=oldscape/digest.csv with 200 and a CSV (from the server's GE data, or an empty valid CSV if none), and the client fetches it once per login instead of every ~10 s (logcat/sidecar log as evidence)
- [ ] docs/client-state.md and docs/app-telemetry.md: drop 'No GE digest fetch captured yet'; add the dated Network QA entry from re/evidence/netqa-ge-digest/notes.txt
- [ ] no request leaves for Jagex hosts (network: re-run the DNS/socket check after the change)

## Log
- 2026-10-20T03:34:37Z created (ready)
- 2026-10-20T03:34:37Z set network=true
- 2026-10-20T03:34:37Z From netqa-ge-digest (cq2, PASS 2026-10-20): 59 digest GETs, all 404 from the sidecar; burst at each login then one per ~10 s while logged in. Official server returns 200 (gen 38: one secure.runescape.com fetch per first login). cq2's docs edit was denied by the QA write hook; text in re/evidence/netqa-ge-digest/notes.txt.
