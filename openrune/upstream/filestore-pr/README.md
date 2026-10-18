# Tracking: long varps compile to the int varp opcodes (OpenRune-Server #352)

Status: **tracked locally, no upstream PR** (decided 2026-10-18). We wait for an upstream fix.

## The bug

https://github.com/OpenRune/OpenRune-Server/issues/352. OpenRune's CS2 compiler
(`me.filby:clientscript-compiler` 0.0.7-openrune, used by OpenRune-FileStore's `PackCs2`) reads and
writes varps typed `long` in `varp.sym` with `PUSH_VARP`/`POP_VARP` (opcodes 1/2). Jagex uses
`PUSH_VAR_LONG`/`POP_VAR_LONG` (64/65). In rev 241 this affects varps 5753-5769 (`ge_tax_slot_long_*`,
`ge_itemsink_price_long_*`): 40 instructions in scripts 778, 779, 5156, 5732, 5733 and 9911.
`ge_itemsink_get` (5733) then returns obj 0 for an empty GE slot, and the offer setup panel shows
"Dwarf remains". Details: `../ISSUE-long-varp-opcodes.md`, `../batch-1-texts/issue.body`.

Maintainer's reply on #352: "I dont support it yet but im aware it needs support in OpenRune-FileStore".

## Why FileStore cannot emit the right opcode

Found by reading the 0.0.7-openrune compiler jars (source is not public):

- `ClientScriptOpcode` has no 64/65 entries at all. Long varcs (51/52) and long locals are there.
- `BinaryScriptWriter.writePushVar`/`writePopVar` map every `VarPlayerType` to PUSH_VARP/POP_VARP
  whatever its inner type. Only `VarClientType` branches on the stack type. No config or feature
  flag changes this.
- `ClientScripts.compileTask(path, rev, logLevel, rewriteAllLibraries)` builds its own (final)
  `OpenRuneScriptWriter` and takes no writer argument. FileStore can only fix up the bytes it gets
  back.

The proper fix is in the compiler: add the two opcodes and branch on the varp's long type in
writePushVar/writePopVar, as for varcs. That's about 10 lines, but only the maintainer can make it.

## Our workaround (stays in our build)

`openrune/scripts/fix_long_varps.py`, run by `scripts/openrune.sh build` after `buildCache`. It
rewrites ops 1/2 to 64/65 for the long varps in the packed cache, then updates idx12 and the
archive-12 reference table.

## Prepared stopgap (kept local, not proposed)

FileStore branch `fix/long-varp-opcodes`, commit e872094, in `re/upstream-pr/filestore`
(git-ignored clone). It does the same rewrite inside `PackCs2`, on the compiled bytes before they
are packed:
- New `LongVarpOpcodes.kt`. The walk takes its operand widths from the compiler's own opcode table
  and must land exactly on the instruction count, otherwise the script is packed as compiled.
- `LongVarpOpcodesTest` (5 tests).
- Verified on a Jagex rev 241 cache: 40 ops in 6 scripts rewritten, and all six scripts match
  Jagex's instruction streams.
- Full `./gradlew build`: no new failures. The pre-existing failures are the cache-fixture tests
  without `data/cache`, and with a rev 241 cache, the npcs round trip (`NpcType [16576]`). Main
  fails these the same way.

Use it if we ever want the fix in FileStore itself, or to offer it upstream.

## How to check for an upstream fix

- OpenRune-FileStore's `tools/build.gradle.kts` depends on a `me.filby:clientscript-compiler`
  newer than `0.0.7-openrune`, or #352 is closed.
- Then rebuild without `fix_long_varps.py` and check script 5733: `%ge_itemsink_price_long_0` must be
  `0040 00001682` (op 64, varp 5762), as in
  https://archive.openrs2.org/caches/runescape/2740/archives/12/groups/5733.dat
- If it is, drop `fix_long_varps.py` from `scripts/openrune.sh`.

## Appendix: the PR texts we would have used

`title` and `body` in this folder hold the FileStore PR as prepared. `issue-reply.md` is the matching
#352 reply; it is unused (its PR link is a placeholder).
