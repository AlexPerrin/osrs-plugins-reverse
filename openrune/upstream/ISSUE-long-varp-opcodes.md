# Issue for upstream: long varps compile to the int varp opcodes

**Where:** OpenRune's clientscript compiler (`me.filby:clientscript-compiler` / `runescript-compiler`
`0.0.7-openrune`), used by `PackCs2` when the cache build recompiles the osrs-dumps scripts.

**What:** a varp typed `long` in `symbols/varp.sym` (rev 241: 5753-5769, `ge_newoffer_price`,
`ge_tax_slot_long_0..7`, `ge_itemsink_price_long_0..7`) is read with `PUSH_VARP` (opcode 1) and
written with `POP_VARP` (opcode 2). Jagex's scripts use `PUSH_VAR_LONG` (64) and `POP_VAR_LONG` (65).
Long varcs are fine (51/52).

**Minimal repro:** `[proc,ge_itemsink_get]` (script 5733), first case:

```
return(%ge_itemsink_obj_0, long_max(0, %ge_itemsink_price_long_0));
```

| | Jagex (OpenRS2 cache 2740, rev 241) | OpenRune build |
|---|---|---|
| `%ge_itemsink_obj_0` | `0001 00000c84` (op 1, varp 3204) | `0001 00000c84` |
| `0` (long) | `003d 0000000000000000` (op 61) | same |
| `%ge_itemsink_price_long_0` | **`0040 00001682` (op 64, varp 5762)** | **`0001 00001682` (op 1)** |
| `long_max` | `0fca` (op 4042) | same |

Check: disassemble group 5733 of archive 12 from the built `.data/cache/LIVE` and from
`https://archive.openrs2.org/caches/runescape/2740/archives/12/groups/5733.dat`.

**Effect in game:** opcode 1 pushes the varp's int, so `long_max` finds one long too few and the
int stack one value too many; the proc returns obj 0 for an empty Grand Exchange slot, and
`ge_offers_switchpanel` (806) shows "Grand Exchange: Offer status" with "Dwarf remains" instead of
the offer setup. Scripts affected in rev 241: 778, 779, 5156, 5732, 5733, 9911 (40 instructions).

**Expected fix:** emit 64/65 for varps whose symbol type is `long`. Until then this repo patches
the packed cache after the build (`openrune/scripts/fix_long_varps.py`).
