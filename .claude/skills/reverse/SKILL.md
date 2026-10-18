---
name: reverse
description: "Reverse-engineer the official OSRS Android client (liblibs.hal.system.osclient.so) with headless Ghidra, jadx or the in-app agent. Use to find or check offsets, struct layouts, xrefs, decompiled functions, patched bytes, or the client's side of the game protocol."
context: fork
---
# Reverse the Android client

Addresses: docs/offsets.md uses ELF addresses; Ghidra is ELF + 0x100000. Protocol notes: docs/android-protocol.md.

## Ghidra (headless, project `re/ghidra`, program `osclient`)
Use `-readOnly` unless the task is to update the project; only one writer at a time.
```
~/tools/ghidra_12.1.4_PUBLIC/support/analyzeHeadless re/ghidra osclient -process osclient.so -noanalysis -readOnly \
  -scriptPath tools/ghidra_scripts -postScript <Script>.java <args>
```
| Script | Args | Output |
|---|---|---|
| `Decompile.java` | Ghidra addresses (`0x76100c ...`) | `re/decomp/<entry>.c` per containing function |
| `Xrefs.java` | Ghidra addresses | functions referencing each |
| `StringXrefs.java` | strings (`"Walk here"`) | functions referencing literals containing them |
| `FindScalar.java` | scalars (`0x89494`) | functions with an instruction using that operand (struct fields) |

## Other tools
- Java side of the APK: `~/tools/jadx/bin/jadx` (output under `re/`).
- Live memory: `tools/agent.py` (base, read, counters, findptr, watch) with `setprop debug.osrsrl.agent 1` and a
  restart; needs the device lock.

## Report
The addresses (say ELF or Ghidra), what each one is, and the evidence path (`re/decomp/*.c`, script output).
A claim you couldn't confirm is marked as a guess.
