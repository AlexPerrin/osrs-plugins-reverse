---
id: client-117hd-feasibility
track: client
title: 'Stretch goal: 117 HD on the Android app (feasibility)'
status: ready
prio: P2
role: client-dev
owner: null
instance: []
branch: null
commit: null
blocked_by: []
unblocks: []
created: '2026-10-20T04:39:08Z'
updated: '2026-10-20T04:39:08Z'
---
## Task
Stretch goal: 117 HD on the Android app (feasibility)

## Definition of done
- [ ] what 117 HD needs on its own terms (its renderer, GL version and features, shaders, scene and model hooks, texture/material data, memory), independent of RuneLite's GPU plugin
- [ ] the gap against the app's GL context (GLES via libnb) and the Android device, from the reverse/agent; reuse the GPU spike's rendering notes when they exist but don't wait for them
- [ ] a ranked plan for the smallest path to a 117 HD frame on the device (e.g. a GLES port of its shaders, reduced feature set), with blockers and a rough size; written to docs and linked from README

## Log
- 2026-10-20T04:39:08Z created (ready)
- 2026-10-20T04:39:08Z Split from client-gpu-117hd-feasibility (user 2026-10-20: 'Consider GPU and HD117 individually'). The user runs 117hd in their own RuneLite.
