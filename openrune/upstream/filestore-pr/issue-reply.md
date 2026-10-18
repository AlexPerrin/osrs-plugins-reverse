I've opened a stopgap on OpenRune-FileStore: https://github.com/OpenRune/OpenRune-FileStore/pull/PLACEHOLDER

Before `PackCs2` packs the compiled scripts, it rewrites ops 1/2 to 64/65 for the varps whose `varp.sym` type is `long`. The walk uses the compiler's own opcode table and must end exactly on the instruction count, otherwise the script is packed unchanged. On a rev 241 build that covers the 40 instructions in 778, 779, 5156, 5732, 5733 and 9911, and all six now match Jagex's scripts. It can be dropped once the compiler emits PUSH_VAR_LONG/POP_VAR_LONG itself.
