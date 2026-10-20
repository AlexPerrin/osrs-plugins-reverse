---
id: client-gpu-117hd-feasibility
track: client
title: 'Stretch goal: GPU plugin and 117 HD on the Android app (feasibility spike)'
status: ready
prio: P2
role: client-dev
owner: null
instance: []
branch: null
commit: null
blocked_by: []
unblocks: []
created: '2026-10-20T04:37:39Z'
updated: '2026-10-20T04:37:39Z'
---
## Task
Stretch goal: GPU plugin and 117 HD on the Android app (feasibility spike)

## Definition of done
- [ ] how the official Android client renders today (GLES version, context ownership, where frames are submitted, the libnb translation layer) written down from the reverse/agent, with offsets or symbols
- [ ] what RuneLite's GPU plugin and 117 HD need from the client and GL (GL version and extensions, compute shaders, DrawCallbacks / scene upload hooks, isGpu) compared with what the app's GL context offers
- [ ] a ranked plan: the smallest path to a working GPU-plugin frame on the device (e.g. a GLES port of the shaders, our own context or overlay, draw-callback hooks), the blockers and a rough size; 117 HD after it
- [ ] docs/client-state.md and README updated: GPU and 117 HD are stretch goals, with this plan linked

## Log
- 2026-10-20T04:37:39Z created (ready)
- 2026-10-20T04:37:39Z User 2026-10-20: 'Our stretch goals as defined initially in this project was GPU and HD117.' Docs had drifted to 'GPU plugins excluded' (README; plugin-host.md ranking skips GPU) and isGpu=false (client-handoff gen ~28). The user runs 117hd and HD in their own RuneLite (project-user-plugins). Prior notes: client-handoff.md 'GLES 3.2' (libnb), the camera/zoom matrix notes around line 210.
