---
id: members-general-stores
track: server
title: 'Members'' general stores: handlers verified from RSProx'
status: ready
prio: P2
role: server-dev
owner: null
instance: []
branch: null
commit: null
blocked_by: []
unblocks: []
created: '2026-10-20T03:00:41Z'
updated: '2026-10-20T03:00:41Z'
---
## Task
Members' general stores: handlers verified from RSProx

## Definition of done
- [ ] transcribe the ~19 candidate RSProx recordings (keeper npc + interface 300) and confirm each keeper -> inv
- [ ] add a handler for every store a recording confirms; resolve Port Phasmatys (233 vs 235) and Quartermaster (159 vs 160) or leave them out
- [ ] unconfirmed stores listed as caveats or left out; league-only recordings marked
- [ ] one PR off upstream main, independent of #371 (no stacking)
- [ ] accuracy spec + green fork CI + Trade checked in game

## Log
- 2026-10-20T03:00:41Z created (ready)
- 2026-10-20T03:00:41Z User approved 2026-10-19 (follow-up to #371): separate PR. sd24 survey: 29 invs, names consistent; 2 verified via upstream captures (lletyageneralshop1, pest_general_store); ~19 with candidate recordings (Burthorpe 3248, Auburnvale 3127, Aldarin 2880, Void 4155, Dorgesh 3189, Lletya 690, Fossil 712, Burgh 3060, Razmire 3088, Pollnivneach 3170, Cam Torum 1693, Quartermaster 2972, Canifis 1637, ...); ~6 league-only; 4 none (Ogre, Ifaba, Port Roberts, West Ardougne).
