---
slug: client-gpu-117hd-feasibility
track: client
title: 'Stretch goal: GPU plugin and 117 HD on the Android app (feasibility spike)'
prio: P2
role: client-dev
stage: live
hold: null
deps: []
branch: null
commit: null
local_only: false
owner: null
instances: []
live_commit: null
created: '2026-10-20T04:37:39Z'
updated: '2026-10-21T00:25:19Z'
---
## Change
Stretch goal: GPU plugin and 117 HD on the Android app (feasibility spike)

## Definition of done
- [ ] how the official Android client renders today (GLES version, context ownership, where frames are submitted, the libnb translation layer) written down from the reverse/agent, with offsets or symbols
- [ ] what RuneLite's GPU plugin and 117 HD need from the client and GL (GL version and extensions, compute shaders, DrawCallbacks / scene upload hooks, isGpu) compared with what the app's GL context offers
- [ ] a ranked plan: the smallest path to a working GPU-plugin frame on the device (e.g. a GLES port of the shaders, our own context or overlay, draw-callback hooks), the blockers and a rough size; 117 HD after it
- [ ] docs/client-state.md and README updated: GPU and 117 HD are stretch goals, with this plan linked

## Log
- 2026-10-21T00:25:19Z migrated from v2 tasks: client-gpu-117hd-feasibility
