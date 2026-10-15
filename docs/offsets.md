# Game offsets

Addresses are ELF virtual addresses of `liblibs.hal.system.osclient.so` (as shown by
readelf/objdump). The Ghidra project in `re/ghidra` loads the library at image base
`0x100000`, so Ghidra address = ELF vaddr + `0x100000`.

## Build 241.4 (GNU build id `f0fff1191f3f235d20857c027044b9c9f709cd8c`)

| Name | Address / offset | How it was found |
|---|---|---|
| `g_client` (Client*) | `0xaee748` | Pointer search for the live Client object; 558 code refs |
| `cycle` (int) | `0xaee6e8` | `agent.py counters`: the only u32 in .data/.bss growing at 50/s |
| Client vtable | `0xa69fe0` | Slot 5 = cycle function; preceded by offset-to-top/RTTI gap |
| Client vtable slot 5: `doCycle` | `0x411640` | The only writer of `cycle` |
| `Client::gameState` (int) | `+0x2020` | Switch in `doCycle` on 0/1/10/1000 and mask `0x10042000000` (25/30/40); reads 10 at the login screen, 30 in game; startup goes 1 → 2 → 5 → 10 |
| varps table | `0x1127ca0` | `{?, u32 count, int* values, ?}`, count 5891. Written by `setVarp` `0x5d06f8(id, value)` from dispatcher cases 34/64; varp 1737 bit 31 confirmed live |
| server varps table | `0x1127c60` | Same layout; `setVarp` writes it first |
| `Client::serverTickCount` | `+0x3058` | Incremented by dispatcher case 55 (SERVER_TICK_END) |
| `Client::changedVarps[32]` / count | `+0x413c5c` / `+0x413cdc` | Ring of varp ids written by VARP_SMALL/LARGE (index = count & 31) |
| walk destination tile x, y | `+0xecdc`, `+0xece0` | Walk diff; -1 when there is no destination (at first it was mistaken for the player position) |
| `Client::worldView` (top-level WorldView*) | `+0xc950` | Passed to the NPC_INFO reader by dispatcher cases 0x91/0x96 (`Client[0x192a]`) |
| WorldView plane, scene size x, y | `wv+0x18`, `wv+0x1c`, `wv+0x20` | Plane follows `::tele … 1/2/0`; size 104 × 104 |
| local player plane | `+0xcb78` | Same teleport diff; just before the scene tile x/y |
| WorldView scene base x, y | `wv+0x24`, `wv+0x28` | 3168, 3168 at Lumbridge; player world = base + scene tile |
| WorldView NPC map | `wv+0x98` buckets, `wv+0xa0` bucket count | Hash map index → Npc*: node `{int index; …; +0x10 Npc*; +0x18 next}`, bucket = index % count |
| WorldView NPC index list / count | `wv+0xd0` (int*), `wv+0xd8` | Rebuilt by NPC_INFO (`0x469778`) |
| Npc: NpcType*, fine x, fine y | `+0x730`, `+0x1fc`, `+0x200` | Live against the test server's 4 NPCs; `+0x268/+0x26c` duplicate x/y; `+0x768` = "updated" flag |
| NpcType: id, name | `+0x0`, `+0x8` | Name is a 24-byte fbstring-style string: inline with `23 - len` in byte 23, else `{char*, size, cap\|flags}` |
| local player scene tile x, y | `+0xcb7c`, `+0xcb80` | Walk diff; world − scene base (3168, 3168 at Lumbridge) |
| local player fine x, height, y | `+0x8950c`, `+0x89510`, `+0x89514` | Walk diff; 128 units per tile, tile centre = ×128 + 64 |
| camera x, height, y | `+0x89488`, `+0x8948c`, `+0x89490` | Swipe diffs; local units (height negative = up) |
| camera pitch, yaw | `+0x89494`, `+0x89498` | 14-bit angles (16384 per turn), the same units as RuneLite 1.13's API; written masked `& 0x3fff` by `0x4517a8`, pitch clamped 0x400–0xbf8 (wider if `+0x8951c` is set) |
| camera pitch/yaw targets | `+0x8949c`, `+0x894a0` | Follow the swipe |
| camera focal point x, height, y | `+0x89500`, `+0x89504`, `+0x89508` | Smoothed player position |
| projection scale | 285 per 720 canvas px (fitted); 199 on the 503-high UI canvas | The game renders on the GPU (no `glUniformMatrix4fv`; likely a UBO). With RuneLite's CPU formula on a 720-px-high canvas, scale 285 puts four NPCs within ~15 px of their figures and stays aligned after rotating the camera. Default zoom only; the zoom field (maybe `+0x894ec`, 576) isn't mapped. Override with `setprop debug.osrsrl.scale N` |
| tile heights | ? | Not found via WorldView pointers; Perspective callers pass an explicit height for now |
| `Client::interfaces` (InterfaceManager*) | `+0x413a98` | Passed to the widget lookup `0x5d1ad8(im, componentId)`; groups at `im+0x6890` (`{?, u64 children, slot*}` × count at `im+0x6888`), slots `{?, Widget*}` |
| top-level interface id | `+0x413a88` | Set by IF_OPENTOP (case 0x4b) |
| open sub-interfaces | `+0x413a60` buckets, `+0x413a68` count | Node `{u32 component, u32 group, …, +0x10 next}`; IF_OPENSUB (case 0x48) → `0x462640(client, component, group, type)` |
| Widget fields | | `+0x78` hidden (IF_SETHIDE 0x4d), `+0x98` colour (IF_SETCOLOUR 0x1d), `+0xfc`/`+0x100` model type/id (IF_SETNPCHEAD 0x3e, IF_SETPLAYERHEAD 0x4f), `+0x10c` sequence (IF_SETANIM 0x1c), `+0x11c`/`+0x120`/`+0x128` rotation/zoom (0x6a), `+0x158` text (IF_SETTEXT 0x7a), `+0xb78` redraw cycle |
| Widget geometry | `+0x5c`/`+0x60` x/y relative to parent, `+0x64`/`+0x68` width/height, `+0x74` parent id (-1 = group root: parent is the component it's mounted in), `+0x06` group (u16) | From CC_GETX/Y/WIDTH/HEIGHT/LAYER in `0x4e5960`; verified with the ::talk dialogue. Units are the game's UI canvas: the root interface (601:0) is 894×503 on a 1920×1080 screen, which the host uses as the overlay canvas |
| Widget geometry (older leads) | | `+0x54`/`+0x58` width/height, `+0x5c`/`+0x60` relative x/y, `+0x64`/`+0x68` second width/height (original?), `+0x94` type?, `+0x128` = 100 (zoom default). From 601:49 (519×145) and 162:568 (479×96 at 20,9) |
| player-info (GPI) state | `Client+0xcb90` | `int count; int indices[count]` = high-resolution (visible) players, the live set; the WorldView map also keeps players that left. Passed to the PLAYER_INFO reader (`0x569a04`, dispatcher case 0x5a) |
| Player name | `*(player+0x718)` → 24-byte string | "Tester" found by pointer scan |
| local player getter | `0x43f8dc(client)` | Used by IF_SETPLAYERHEAD; player `+0x728` = appearance |
| world views map | `Client+0xc878` | `{…, +0x20 buckets, +0x28 count}`, key = world view id (`Client+0xcb10` for the local player's), value WorldView* (`0x4ae3d8`) |
| WorldView player map | `wv+0x68` buckets, `wv+0x70` count | Node `{int index; +0x10 Player*; +0x18 next}` (`0x4b0da4`); local player index at `Client+0xcb0c`. Player/NPC likely share the Actor layout (fine x/y `+0x1fc/+0x200`): unverified |
| Script VM (static object) | `0xaef1d0` (GOT `0xa82700`) | `+0x04` running flag, `+0x08` int locals (vector), `+0x20` long locals, `+0x38` object locals (0x28-byte variants), `+0x50` int stack (1000 ints) with size `+0xff0`, `+0xff8` object stack (1000 variants) with size `+0xac38`, `+0xcbc0` call-frame count, `+0xded8`/`+0xdee8` active widget (CC/dot), `+0xe0d4` error status |
| `runScriptEvent(vm, ScriptEvent*, maxOps)` | `0x4df54c` | Called via `0x4dc270(ev, maxOps)`. ScriptEvent: `+0x08` argc, `+0x10` args (variants; arg 0 = script id), `+0x40` source widget, `+0x48..+0x6c` values for RuneLite's magic int args (mouse x/y, op index, ...), `+0x70` opbase string, `+0x88` → vm+0xe0cc, `+0x8c` trigger type (other than 10–17/0x12/0x55/0x56 = look the script up by id with `0x3f5658`). Verified: script 4029 (quest status) returns the right state |
| Script variant | 0x28 bytes | 32-byte payload (int, int64, or 24-byte string) + u32 type at `+0x20`: 1 int, 2 string, 4 object (shared_ptr), 5 long; -1 empty |
| Script interpreter loop | `0x4dd08c(vm, Script shared_ptr*, maxOps)` | Opcodes < 100 inline (0 push int, 3 push string, 6 jump, 21 return, 25/27 varbit get/set, 33/34 int local load/store, ...), ≥ 100 through `0x4e06dc` (`0x4e5960` = CC_GET*). Script: `+0x30` opcodes, `+0x48` int operands, `+0x60` string operands, `+0x80/84/88` int/long/object local counts, `+0x8c/90/94` arg counts |
| `getObjType(id)` → `shared_ptr<ObjType>` | `0x63bfe8` | Definition cache lookup/load used by the OC_* script ops (`0x4bcf44`, opcodes 4200–4255). Returns via x8 (non-trivial type), result `{control block, ObjType*}`; control block = vtable, int uses `+8`, int weak `+0xc` |
| ObjType fields | | `+0x08` name (24-byte string, " (Members)" appended on F2P worlds), `+0x20` string (op 0x107a), `+0xd4` stackable (==1), `+0xd8` cost, `+0xdc/+0xe0/+0xe4` wear pos 1–3, `+0xe8` members (byte), `+0xf0` ground ops, `+0x108` inventory ops (5 × 24-byte strings, pointer), `+0x278` category, `+0x2b0` linked note id, `+0x2b4` note template (799 when noted), `+0x2d0` weight (grams), `+0x308` byte (op 4220; 1 for coins/pot/axe, 0 for whip: tradeable? unverified), `+0x314` placeholder id, `+0x318` placeholder template. Verified on coins, pot (noted 1932), bronze axe, whip |

`doCycle` is reached only through the vtable (no direct calls), so `client_thread.cpp` hooks it by
rewriting vtable slot 5 (`0xa6a008`, in .data.rel.ro) after checking that it holds `0x411640`.

## Leads (not yet confirmed)

| Function | Evidence |
|---|---|
| `0x41aa80` | References "Login completed" |
| `0x5d0040` | Varbit read: `varps[def.index] >> def.lsb & mask[msb - lsb]`; varbit defs via `0x64b450`, varp values at `0x1127cb0` |
| next chat message id | `0xaab658` | Global counter, incremented by the Message constructor `0x3f0a04` |
| chat messages by id | `0xaab628` | Hash map `{?, buckets +8, u32 count +0x10}`; node `{int id; +0x10 Message*; +0x18 next}` |
| chat channels | `0xaab5e0` | Map type → channel: 100 × `{ctrl*, Message*}` ring, count `+0x640`, write index `+0x644` (`0x3ef964`) |
| Message | | `+0x00` id node (`node+0x10` = id), `+0x08` cycle, `+0x40` type, `+0x48` name, `+0x70` sender, `+0xa0` text (24-byte strings) |
| addChatMessage | `0x43a9c0(client, type, name, text)` | Called by MESSAGE_GAME (dispatcher case 0x51) |
| `0x441dd4` | "Unrecognized packet type: %d" |
| `0x51a7fc`, `0x50621c` | Only readers of the global "Walk here" `std::string` (`0x18d1a50`) → menu builders |
| `0x5820b4` | Console `~debugproc` help → command table, script VM entry |
| `0x985074` | `getcamerapos` console command → camera fields |
