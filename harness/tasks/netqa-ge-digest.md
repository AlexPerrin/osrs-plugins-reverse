---
id: netqa-ge-digest
track: client
title: 'Network QA: capture with a GE digest fetch'
status: done
prio: P3
role: client-qa
owner: null
instance:
- device
- desktop
branch: master
commit: 6d82e5db9380b7aaada4b2e996b758c97ba6731e
evidence: /home/alex/osrs-plugins-reverse/re/evidence/netqa-ge-digest
blocked_by: []
unblocks: []
created: '2026-10-20T01:42:43Z'
updated: '2026-10-20T03:34:25Z'
summary: 'PASS on build 6d82e5d. GE digest goes to the sidecar only (59 digest.csv GETs on 10.0.2.2:8080, bursts at each of 3 logins then every 10 s); no runescape or jagex lookups or sockets; expected
  hosts only; 7 telemetry lines. Findings in notes.txt: sidecar answers digest.csv with 404 so the client polls every 10 s; the hook denied my app-telemetry.md heading; WSL interop timeouts forced a device-side
  sampler'
---
## Task
Opportunistic: a Network QA capture that includes a GE digest fetch, which should go to the sidecar, not Jagex (docs/client-state.md Next tasks 4).

## Definition of done
- [x] capture in re/evidence/netqa-ge-digest/ shows the GE digest request to the sidecar host only; expected hosts only, telemetry patch log clean

## Log
- 2026-10-20T01:42:43Z created (ready)
- 2026-10-20T03:16:26Z claimed by cq2 (instances device,desktop)
- 2026-10-20T03:33:54Z handover master@6d82e5db9: PASS on build 6d82e5d. GE digest goes to the sidecar only (59 digest.csv GETs on 10.0.2.2:8080, bursts at each of 3 logins then every 10 s); no runescape or jagex lookups or sockets; expected hosts only; 7 telemetry lines. Findings in notes.txt: sidecar answers digest.csv with 404 so the client polls every 10 s; the hook denied my app-telemetry.md heading; WSL interop timeouts forced a device-side sampler
- 2026-10-20T03:34:19Z handover-check FAIL (2 items; see the check output)
- 2026-10-20T03:34:19Z set status=ready owner=
- 2026-10-20T03:34:19Z back to ready after the hand-over check FAIL; the next dev fixes exactly those items
- 2026-10-20T03:34:25Z Network QA PASS (cq2): GE digest only to the sidecar (59 GETs, 10.0.2.2:8080), expected hosts only, telemetry patch log 7 lines. Evidence re/evidence/netqa-ge-digest/. Closed by the orchestrator: a QA capture has no branch to merge, so the hand-over check's merge items don't apply.
- 2026-10-20T03:34:25Z done
