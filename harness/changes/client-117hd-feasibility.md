---
slug: client-117hd-feasibility
track: client
title: 'Stretch goal: 117 HD on the Android app (feasibility)'
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
Stretch goal: 117 HD on the Android app (feasibility)

## Definition of done
- [ ] what 117 HD needs on its own terms (its renderer, GL version and features, shaders, scene and model hooks, texture/material data, memory), independent of RuneLite's GPU plugin
- [ ] the gap against the app's GL context (GLES via libnb) and the Android device, from the reverse/agent; reuse the GPU spike's rendering notes when they exist but don't wait for them
- [ ] a ranked plan for the smallest path to a 117 HD frame on the device (e.g. a GLES port of its shaders, reduced feature set), with blockers and a rough size; written to docs and linked from README

## Log
- 2026-10-21T00:25:19Z migrated from v2 tasks: client-117hd-feasibility
