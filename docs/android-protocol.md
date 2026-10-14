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
| 42 | OBJ_ADD_SPECIFIC | timeUntilPublic `p2`, coord `p4Alt3`, ownership `p1Alt2`, neverPublic `p1Alt2`, id `p2`, timeUntilDespawn `p2Alt2`, opFlags `p1Alt2`, quantity `p4` (not yet seen rendering) |
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

Recognised but not implemented: 8 UPDATE_INV_PARTIAL, 16 LOC_DEL (zone), 18
UPDATE_INV_STOPTRANSMIT, 35 LOGOUT_TRANSFER, 41 REBUILD_REGION_V2, 70 REBUILD_WORLDENTITY,
117/43/9 clan and friends-chat messages. Not found yet: WORLDENTITY_INFO_V7 (the server
skips it; nothing breaks without world entities).

## Player extended info

Flag bytes: 1–3; `0x80` in the first byte announces a second, `0x4000` a third (desktop uses
`0x2000`). Blocks are read in this order:

`0x2` reset, `0x200` tinting, `0x4` sequence, `0x20000` hitmarks, `0x40` ?, `0x100` move speed,
`0x8000` ? (three strings), `0x2000` temp move speed, `0x10000` ?, `0x400` chat, `0x10`
appearance, `0x1` say, `0x80000` ?, `0x100000` ?, `0x20` ?, `0x1000` ?, `0x40000` headbars

| Block | Layout |
|---|---|
| sequence `0x4` | id `p2Alt2`, delay `p1` |
| move speed `0x100` | `p1Alt2` |
| temp move speed `0x2000` | `p1Alt1` (127 = teleport) |
| chat `0x400` | colour<<8\|effects `p2Alt3`, modicon `p1Alt1`, autotyper `p1`, huffman length `p1Alt2`, huffman `pdataAlt3`, pattern bytes `p1Alt1` |
| appearance `0x10` | length `p1Alt1`, payload `pdataAlt3` (reversed, +128); payload as desktop |

## Client→server (identified)

| Opcode | Prot | Layout |
|---|---|---|
| 31 | MOVE_GAMECLICK | x `g2Alt3`, z `g2Alt2`, keyCombination `g1Alt1` |
| 64 | MESSAGE_PUBLIC | as desktop (type, colour, effect, huffman) |
| 81 | CLIENT_CHEAT | as desktop (string, autocomplete byte) |
| 93 | NO_TIMEOUT (probable) | — |
| 48 | RESUME_PAUSEBUTTON | component `g4Alt3`, sub `g2` |
| 99 | IF_BUTTONX | as desktop (combined id, sub, obj, op); inventory item ops arrive as 149:0 with sub = slot, op 3 = wield/wear |

Seen but unidentified: 37 (keyboard events), 10 (5 bytes after login, looks like WINDOW_STATUS: `02 0780 0438` =
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
48 RESUME_PAUSEBUTTON (component `g4Alt3`, sub `g2`); the server closes with IF_CLOSESUB.
Known gap: the chat history stays hidden after closing (2379's effect is not undone).
