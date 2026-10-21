---
slug: client-gpu-plugin-feasibility
track: client
title: 'Stretch goal: RuneLite''s GPU plugin on the Android app (feasibility)'
prio: P2
role: client-dev
stage: ready
hold: null
deps: []
branch: null
commit: null
local_only: false
owner: null
instances: []
created: '2026-10-20T04:39:08Z'
updated: '2026-10-21T00:25:19Z'
---
## Change
Stretch goal: RuneLite's GPU plugin on the Android app (feasibility)

## Definition of done
- [ ] how the official Android client renders today (GLES version, context ownership, frame submission, the libnb translation layer), from the reverse/agent with offsets or symbols
- [ ] what the GPU plugin needs (GL version and extensions, compute shaders or its non-compute path, DrawCallbacks and scene upload, isGpu) against what the app's GL context offers
- [ ] a ranked plan for the smallest path to a GPU-plugin frame on the device, with blockers and a rough size; written to docs and linked from README

## Log
- 2026-10-21T00:25:19Z migrated from v2 tasks: client-gpu-plugin-feasibility
