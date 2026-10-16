# osrs-plugins-reverse

RuneLite plugins on the official Old School RuneScape Android app. Inspired by
[AnyPS5](https://github.com/boykopovar/AnyPS5): no emulator and no rewritten client. The real
Android build (241.4) is repacked to load `libosrsrl.so`, which hooks the native game
(`liblibs.hal.system.osclient.so`), reads its state, and runs an in-process Java plugin host. That
host loads unmodified RuneLite plugins on ART. The target plugin is **Quest Helper**.

Development runs against a rev-241 test server (`server/`) that speaks the Android game protocol.
That protocol differs from the desktop one, and no public mapping of it exists. Nobody needs a Jagex
account: the server is also the local sign-in provider.

## Status

<!-- progress:start -->
![Mobile packets mapped](https://img.shields.io/badge/Mobile%20packets%20mapped-24.9%25-blue) ![Mobile packets implemented](https://img.shields.io/badge/Mobile%20packets%20implemented-22.5%25-blue) ![RuneLite API implemented](https://img.shields.io/badge/RuneLite%20API%20implemented-30.1%25-blue)

- **Mobile packets mapped**: 63/253 (24.9%)
- **Mobile packets implemented**: 57/253 (22.5%)
- **Server→client mapped**: 38/151 (25.2%)
- **Client→server mapped**: 25/102 (24.5%)
- **Zone sub-prots implemented**: 4/15 (26.7%)
- **RuneLite API implemented**: 261/866 (30.1%)

Details: [docs/progress.md](docs/progress.md) (as of 2026-10-15, `87096c4+`).
<!-- progress:end -->

Milestones:
- [x] Repacked APK loads the hook library; ImGui overlay; game-thread hook (`doCycle`)
- [x] Test server: JS5, login, Lumbridge, walking, chat, inventory/equipment, NPCs, bank, dialogues
- [x] Plugin host on ART: events, varps/varbits, players/NPCs, widgets, overlays via Graphics2D shim
- [x] Server: collision + pathfinding, melee combat, journal tab, Cook's Assistant end to end
- [x] Quest Helper (real Plugin Hub jar) runs: native sidebar, quest list, step overlay
- [ ] Quest Helper complete: menu entries, object/item highlights, minimap arrows, text input
- [ ] Protocol: map the remaining opcodes (see `docs/progress.md` → Unmapped opcodes)
- [ ] More plugins (GPU/region-dependent ones excluded)

## Layout

| Path | What | Owner | Notes |
|---|---|---|---|
| `native/` | `libosrsrl.so`: hooks, game accessors, JNI bridge, overlay | client dev | `docs/client-handoff.md`, `docs/offsets.md` |
| `host/` | Java plugin host (RuneLite API on the Android game) | client dev | `docs/plugin-host.md` |
| `server/` | Kotlin RSPS + Android codecs (`server/rsprot.patch` over RSProt) | server dev | `docs/server-handoff.md`, `docs/android-protocol.md` |
| `tools/` | repack/patch scripts, Ghidra helpers, memory agent, `progress.py` | shared | |
| `re/` | git-ignored inputs/outputs (APK libs, Ghidra project, jars, builds) | | |

## Progress tracking

`tools/progress.py` recomputes every number above from the code: the Android prot enums and
codec bindings (against the client's opcode size tables) and the RuneLite API methods the host
actually overrides (against its generated throwing stubs). It rewrites `docs/progress.md`, this
status block, and one row per day in `docs/progress-history.csv`. Run it before each milestone
commit (it needs `server/rsprot` from `server/scripts/rsprot.sh init` and `host/build.sh` output).

## Disclaimer

This is for research and interoperability. It distributes no Jagex code or assets. APKs, the game
cache and decompiled output stay out of git. Use the official servers only with the unmodified
app.

🦉
