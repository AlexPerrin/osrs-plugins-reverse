# Android game protocol (build 241.4)

The official Android app logs in as `ENHANCED_ANDROID` (client type 7) and speaks its own game
protocol: opcodes are permuted relative to RSProt's desktop protocol **and** field orders/byte
transforms differ per packet. Login and JS5 are the same as desktop. The Android codecs live in
`server/rsprot` (module `osrs-241-android`, kept as `server/rsprot.patch`).

Addresses below are Ghidra addresses (ELF vaddr + 0x100000).

## Where things are in the client

| What | Where |
|---|---|
| Server→client size table (151 × int32, by opcode) | .rodata `0x104c4c` (file offset), via GOT `0xb826c8` |
| Client→server size table (102 × int32, by opcode) | .rodata `0x109538`, via GOT `0xb84320` |
| Packet reader (opcode + size, ISAAC) | `0x5da16c` |
| Packet dispatcher, one `case` per server opcode | `0x541dd4` |
| Outgoing packet constructor `(pkt, opcode, isaac)` | `0x79cc90` (opcode is a literal at each call site) |
| Login completion: reads gpi init then REBUILD fields | `0x51aa80` |
| PLAYER_INFO reader (bits + extended info) | `0x669a04` |
| NPC_INFO reader (flag: small/large) | `0x569778` |
| Config validator (param 15 = 0 requires jagex.com/runescape.com URL) | `0x50ee18`, `0x83d254` |
| Login RSA modulus (parsed by static init) | string at `0x1d8ebc`, `_INIT_141` |

Both size tables are permutations of RSProt's desktop sizes (plus one entry for desktop's
unassigned opcode), which is what made them easy to find.

## Byte transforms

| Client read | RSProt write |
|---|---|
| `b ^ 0x80` | `p1Alt1` |
| `-b` | `p1Alt2` |
| `-0x80 - b` (128 − b) | `p1Alt3` |
| `[lo^0x80][hi]` | `p2Alt3` |
| `[hi][lo^0x80]` | `p2Alt2` |
| native int, no swap | `p4Alt1` (little endian) |
| native int/short behind the `FUN_00b40c20` swap | `p4` / `p2` (big endian) |

## Server→client (identified)

| Opcode | Prot | Layout |
|---|---|---|
| 2 | UPDATE_STAT_V2 | current `p1`, boosted `p1Alt3`, stat `p1Alt1`, xp `p4Alt1` |
| 8 | UPDATE_INV_PARTIAL | as desktop |
| 6 | REBUILD_NORMAL_V2 (+ login variant: gpi block first) | worldArea `p2Alt3`, zoneX `p2`, zoneZ `p2` |
| 10 | RUNCLIENTSCRIPT | as desktop |
| 26 | UPDATE_RUNWEIGHT | `p2` |
| 28 | IF_SETANIM | anim `p2Alt1`, component `p4` |
| 30 | SET_NPC_UPDATE_ORIGIN | `p1`, `p1` |
| 32 | UPDATE_INV_FULL | header as desktop; per slot id+1 `p2Alt2`, count `p1Alt1`, big count `p4Alt3` |
| 42 | OBJ_ADD_SPECIFIC | timeUntilPublic `p2`, coord `p4Alt3`, ownership `p1Alt2`, neverPublic `p1Alt2`, id `p2`, timeUntilDespawn `p2Alt2`, opFlags `p1Alt2`, quantity `p4`; timeUntilDespawn 0 = expires immediately |
| 47 | IF_SETNPCHEAD_ACTIVE | component `p4Alt3`, slot `p2Alt2` |
| 34 | VARP_SMALL | value `p1Alt2`, id `p2` |
| 46 | LOGOUT | — |
| 55 | SERVER_TICK_END (probable) | — |
| 59 | IF_SETEVENTS_V2 | end `p2`, events1 `p4Alt3`, combined id `p4`, events2 `p4Alt1`, start `p2Alt3` (ops are events2 bits) |
| 62 | IF_SETNPCHEAD | component `p4`, npc `p2Alt1` |
| 64 | VARP_LARGE | id `p2Alt2`, value `p4Alt1` |
| 72 | IF_OPENSUB | dest `p4`, type `p1Alt3`, interface `p2Alt2` |
| 75 | IF_OPENTOP | `p2Alt3` |
| 77 | IF_SETHIDE | hidden `p1Alt3`, component `p4` (from client session; not implemented) |
| 79 | IF_SETPLAYERHEAD | component, middle-endian (from client session; not implemented) |
| 81 | MESSAGE_GAME | as desktop |
| 90 | PLAYER_INFO | bit layout as desktop; extended info below |
| 99 | SET_ACTIVE_WORLD_V2 | `p2`, `p1` (as desktop) |
| 101 | IF_CLOSESUB | component `p4` |
| 102 | UPDATE_RUNENERGY | `p2` |
| 109 | OBJ_DEL_SPECIFIC | coord `p4Alt1`, id `p2Alt2`, quantity `p4Alt1` |
| 122 | IF_SETTEXT | component `p4Alt3`, string |
| 145 / 150 | NPC_INFO_LARGE_V6 / NPC_INFO_SMALL_V6 | RSProt's NpcInfo bitstream; new NPCs use the Android order below |

| 7 | UPDATE_ZONE_PARTIAL_FOLLOWS | z `p1Alt3`, x `p1Alt1`, level `p1Alt1` |
| 68 | UPDATE_ZONE_FULL_FOLLOWS | level `p1Alt1`, z `p1Alt1`, x `p1Alt2` |
| 23 | UPDATE_ZONE_PARTIAL_ENCLOSED | level `p1Alt1`, z `p1Alt3`, x `p1`, then (Android zone index `p1`, payload)* |
| 4 / 14 / 16 / 17 / 48 / 105 | MAP_ANIM / LOC_ADD_CHANGE_V2 / LOC_DEL / LOC_ANIM / SOUND_AREA / LOC_MERGE | top-level zone prots, payload as in the zone table below |

Recognised but not implemented: 8 UPDATE_INV_PARTIAL, 18
UPDATE_INV_STOPTRANSMIT, 35 LOGOUT_TRANSFER, 41 REBUILD_REGION_V2, 70 REBUILD_WORLDENTITY,
117/43/9 clan and friends-chat messages. Not found yet: WORLDENTITY_INFO_V7 (the server
skips it; nothing breaks without world entities).

## Zone packets

Zone x/z in the headers are build-area tiles of the zone's south-west corner (build area starts
6 zones south-west of the rebuild zone); level is the plane. Zone prots carry coord-in-zone
`x << 4 | z`. All zone prots are handled by one function (Ghidra `0x56edb0`) that switches on
an Android index, which is also the sub-opcode inside PARTIAL_ENCLOSED:

| Index | Prot | Size | Payload (implemented ones) |
|---|---|---|---|
| 0 | MAP_ANIM | 6 | |
| 1 | LOC_ADD_CHANGE_V2 | var | op count `p1Alt2`, (op index 0-4 `p1`, string)*, shape<<2\|rot `p1`, coord `p1`, opFlags `p1Alt3`, id `p2Alt2` |
| 2 | LOC_ANIM | 4 | |
| 3 | OBJ_ENABLED_OPS | 4 | |
| 4 | OBJ_UNCUSTOMISE | 7 | |
| 5 | OBJ_DEL | 7 | coord `p1`, quantity `p4Alt2`, id `p2Alt3` |
| 6 | MAP_PROJANIM_V2 | 24 | |
| 7 | OBJ_CUSTOMISE | 17 | |
| 8 | OBJ_COUNT | 11 | |
| 9 | LOC_MERGE | 14 | |
| 10 | LOC_DEL | 2 | shape<<2\|rot `p1Alt3`, coord `p1` |
| 11 | OBJ_ADD | 14 | neverPublic `p1Alt1`, timeUntilDespawn `p2Alt1`, quantity `p4Alt1`, ownership `p1Alt1`, coord `p1`, id `p2`, opFlags `p1Alt2`, timeUntilPublic `p2Alt2` |
| 12 | SCRIPTEDPROJ_ADD | | |
| 13 | SOUND_AREA | 7 | |
| 14 | SCRIPTEDPROJ_CHANGE | 18 | |

Indices were matched by payload size and by what each case does with the tile's loc/obj lists
(OBJ_ADD's argument order against OBJ_ADD_SPECIFIC, which calls the same function `0x55775c`).
The server sends them with `AndroidUpdateZonePartialEnclosed` + `AndroidZoneProts.encode`.

## Player extended info

Flag bytes: 1–3; `0x80` in the first byte announces a second, `0x4000` a third (desktop uses
`0x2000`). Blocks are read in this order:

`0x2` reset, `0x200` tinting, `0x4` sequence, `0x20000` headbars, `0x40` ?, `0x100` move speed,
`0x8000` ? (three strings), `0x2000` temp move speed, `0x10000` ?, `0x400` chat, `0x10`
appearance, `0x1` say, `0x80000` ?, `0x100000` ?, `0x20` ?, `0x1000` ?, `0x40000` hitmarks

| Block | Layout |
|---|---|
| sequence `0x4` | id `p2Alt2`, delay `p1` |
| move speed `0x100` | `p1Alt2` |
| temp move speed `0x2000` | `p1Alt1` (127 = teleport) |
| chat `0x400` | colour<<8\|effects `p2Alt3`, modicon `p1Alt1`, autotyper `p1`, huffman length `p1Alt2`, huffman `pdataAlt3`, pattern bytes `p1Alt1` |
| appearance `0x10` | length `p1Alt1`, payload `pdataAlt3` (reversed, +128); payload as desktop |
| headbars `0x20000` | count `p1`; per bar: type smart, endTime smart (0x7FFF = remove, nothing follows), startTime smart, startFill `p1Alt2`, endFill `p1Alt3` only if endTime != 0 (client: `0x572780` → `0x575130(cycle+startTime, startFill, endFill, endTime)`) |
| hitmarks `0x40000` | count `p1Alt1`; per hit: type, value, delay, limit, all smart1or2 (client: `0x572240(type, value, delay, cycle, limit)`) |

## Client→server (identified)

| Opcode | Prot | Layout |
|---|---|---|
| 31 | MOVE_GAMECLICK | x `g2Alt3`, z `g2Alt2`, keyCombination `g1Alt1` |
| 64 | MESSAGE_PUBLIC | as desktop (type, colour, effect, huffman) |
| 81 | CLIENT_CHEAT | as desktop (string, autocomplete byte) |
| 93 | NO_TIMEOUT (probable) | — |
| 48 | RESUME_PAUSEBUTTON | component `g4Alt1` (int LE), sub `g2Alt1` (LE); chatmenu options arrive as 219:1 sub = option (1-based) |
| 99 | IF_BUTTONX | as desktop (combined id, sub, obj, op); inventory item ops arrive as 149:0 with sub = slot, op 3 = wield/wear |
| 7, 55, 58, 3, 66 | OPLOC1..5 | see below (sender Ghidra `0x61d10c`; verified OPLOC1 on device) |
| 38 | OPLOC6 (examine) | id `g2` (observed `0464` = bush 1124) |
| 63 | OPLOCT | ctrl `g1Alt1`, selected combined id `g4Alt1`, id `g2Alt1`, selected sub `g2Alt3`, selected obj `g2Alt2`, x `g2`, z `g2` (sender `0x61cfa8`) |
| 74 | OPOBJT | selected sub `g2`, ctrl `g1Alt2`, id `g2`, selected combined id `g4`, z `g2Alt2`, x `g2Alt2`, selected obj `g2Alt1` (sender `0x6205c4`) |
| 2 | OPNPCT | selected sub `g2Alt2`, index `g2Alt2`, selected combined id `g4Alt3`, ctrl `g1Alt3`, selected obj `g2Alt3` (sender `0x61f9fc`) |
| 38 / 6 / 49? | OPLOC6 / OPOBJ6 / OPNPC6? | examine; 6 (6 bytes) is OPOBJ6's sender `0x6211ac`, 49 (2 bytes) sender `0x620400` |

The T packets (63, 74, 2) were decoded from the senders and verified live (bush, cabbage, Hans; inventory "Use" needs events1 target bits 11-16). Other single-packet
senders near them: 5 (11 bytes, `0x622bd4`, probably OPWORLDENTITYT), 69 (`0x623550`), 45
(`0x623960`), 35 (`0x623a28`), 31 = MOVE_GAMECLICK (`0x61cbb8`).
| 79, 24, 82, 34, 40 | OPOBJ1..5 | see below (sender `0x62073c`) |
| 92, 44, 71, 33, 9 | OPNPC1..5 | see below (sender `0x61fc20`) |

Op packets (fields in wire order; `p2a2` = `[hi][lo+128]`, `p2a3` = `[lo+128][hi]`, `p2a1` =
little endian, `p1a1` = b+128, `p1a2` = -b, `p1a3` = 128-b; x/z are absolute coordinates, ctrl
is the run modifier, subop is the minimenu sub-op):

| Packet | Layout |
|---|---|
| OPLOC1 (7) | id p2a2, subop p1a3, x p2a2, z p2, ctrl p1a2 |
| OPLOC2 (55) | id p2a2, z p2, subop p1a1, x p2a2, ctrl p1a1 |
| OPLOC3 (58) | ctrl p1, x p2, z p2a3, id p2a1, subop p1a3 |
| OPLOC4 (3) | x p2, id p2, ctrl p1a2, z p2a1, subop p1a1 |
| OPLOC5 (66) | subop p1a3, ctrl p1a1, x p2a3, z p2a1, id p2a1 |
| OPOBJ1 (79) | id p2, subop p1a1, z p2a2, x p2, ctrl p1a1 |
| OPOBJ2 (24) | id p2a2, ctrl p1a2, x p2a3, subop p1, z p2a1 |
| OPOBJ3 (82) | x p2a2, id p2a1, ctrl p1a1, subop p1a1, z p2a2 |
| OPOBJ4 (34) | subop p1a1, ctrl p1a2, id p2a3, x p2, z p2a2 |
| OPOBJ5 (40) | id p2a2, ctrl p1a3, x p2a3, z p2a2, subop p1a2 |
| OPNPC1 (92) | index p2a3, ctrl p1, subop p1a1 |
| OPNPC2 (44) | index p2, subop p1, ctrl p1a1 |
| OPNPC3 (71) | ctrl p1, subop p1, index p2 |
| OPNPC4 (33) | subop p1a1, ctrl p1a3, index p2a3 |
| OPNPC5 (9) | index p2a2, ctrl p1a2, subop p1a1 |

The op senders were found by listing every call to the packet constructor `0x79cc90` (opcode
is the second argument): functions with five calls are the op1-5 families (OPPLAYER1-8 is
`0x623e7c`, OPWORLDENTITY1-5 `0x622f10`). OPLOC vs OPOBJ was told apart by OPLOC6/OPLOCT sharing
the loc helper `0x623b5c`. A loc/obj x/z swap would show as a wrong tile; OPLOC1 was checked live.

Seen but unidentified: 18 (8 bytes after a minimenu "Walk here": `0000ffff00ee01bf`), 37 (keyboard events), 10 (5 bytes after login, looks like WINDOW_STATUS: `02 0780 0438` =
mode 2, 1920×1080), 13, 21 (6 bytes per tap, mouse click event), 67, 98, 100. The server logs
every unidentified packet with `LOG_ANDROID_PACKETS=true`.

## Display name

The chatbox refuses input ("You must set a name before you can chat") unless varbit 8119
`has_displayname_transmitter` (varp 1737 bit 31) is set; clientscripts 88, 107, 111, 125, 129,
223, 550, 681 and 7822 check it. The server sets it at login. It is unrelated to whether the Jagex
account has a character name: the name shown in game is the one the server sends in the
appearance block.

## Mobile gameframe notes

The logout panel is opened from the door icon in the top-left corner of `toplevel_osm` (the
debug overlay of libosrsrl covers it; `setprop debug.osrsrl.overlay 0` to hide the overlay).
Its "Tap here to logout" button arrives as IF_BUTTONX on interface 182, as on desktop.

## Reconnect

Reconnect uses the login protocol's `ReconnectOk` (same as desktop): the client presents the
previous session's XTEA seed, the server resets player/npc/world-entity info and replies with
the gpi init block. The test server keeps a dropped player for 60 s; `::drop` closes the
connection without logging out to exercise it.

## NPC info

New (low-resolution → high-resolution) NPCs are written in this order on Android: index (16),
dx (6 bits small / 8 bits large), dz, spawn-cycle flag (1) [+ size index (2) + value
(18/19/20/32 bits)], type size index (2) + type (12/14/17/24 bits), extended-info flag (1),
direction (3), jump (1). Desktop sends dz first and dx last, and jump/direction/spawn before the
type.

NPC extended info: flag bytes chain with `0x20` (second byte), `0x2000` (third) and `0x20000`
(fourth). Read order starts 0x2 transformation, 0x800000 (3 bytes), 0x200000 (1 byte), 0x80
(hits, count `-b`), 0x200 (1 byte `-b`), 0x1 sequence, 0x4 say, 0x1000, ...

| Block | Layout |
|---|---|
| transformation `0x2` | id `p2Alt2` (as desktop) |
| sequence `0x1` | id `p2Alt3`, delay `p1` |
| say `0x4` | `pjstr` (as desktop) |
| headbars `0x1000000` | count `p1Alt2`; per bar: type smart, endTime smart (0x7FFF = remove), startTime smart, startFill `p1Alt1`, endFill `p1` if endTime != 0 |
| hitmarks `0x40000` | count `p1`; per hit: type, value, delay, limit (smart1or2) |

Full NPC read order (flag tests in 0x569778): 0x2, 0x800000, 0x200000, 0x80 (spotanims), 0x200,
0x1, 0x4, 0x1000, 0x800, 0x1000000 (headbars), 0x10, 0x8, 0x100, 0x2000000, 0x400, 0x40,
0x40000 (hitmarks), 0x4000, 0x10000, 0x80000, 0x100000, 0x8000, 0x400000.

Other NPC blocks are not mapped; the Android writer drops them.

## Local sign-in (no Jagex account)

With `LOCAL_AUTH=true` (server) and a client built with `LOCAL_AUTH=1 SERVER=... tools/repack.sh`,
the test server replaces Jagex's sign-in:

- jav_config params 11, 20, 22 and 28 (auth.jagex.com, social.auth.jagex.com,
  auth.runescape.com, account.jagex.com) point at the server's HTTP base.
- The Java SDK (AppAuth) runs OpenID Connect against it: discovery, `oauth2/auth` (redirects
  straight back with a code), `oauth2/token` (unsigned ID token echoing the nonce).
- Native code then calls the game-session API at the param-11 base: `POST sessions`,
  `GET accounts` (one character, `LOCAL_DISPLAY_NAME`) and `POST tokens`. That token goes into
  the game login as auth type 7.
- Client patches (tools/patch_local_auth.py): cleartext allowed in network_security_config, and
  AppAuth's "only https connections" and "Issuer must be an https URL" checks removed.

In-app: "Sign in with Email / Username", close the privacy screen, then Play Now. The client
ID, scope and redirect URI are hard-coded in native code (Ghidra 0x59d638).

## Dialogue

`::talk` opens chat_left (231) in **chatbox:chatmodal = 162:568** (rev 241; it was 162:566 in
rev 239, whose symbols this server's ids come from. Component ids can shift between revisions).
Sequence: RUNCLIENTSCRIPT 2379 (reset background), IF_OPENSUB, IF_SETNPCHEAD, IF_SETANIM,
IF_SETTEXT name/text/continue, IF_SETEVENTS (pause button). Tapping continue sends client opcode
48 RESUME_PAUSEBUTTON (component int LE, sub LE); the server closes with IF_CLOSESUB.
Known gap: the chat history stays hidden after closing (2379's effect is not undone).
