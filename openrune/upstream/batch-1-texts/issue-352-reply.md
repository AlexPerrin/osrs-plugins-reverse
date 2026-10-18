Thanks, that makes sense. Until FileStore supports it, we work around it in our build with a small post-pass after `buildCache`: it rewrites ops 1/2 to 64/65 for the varps whose `varp.sym` type is `long` (40 instructions in 778, 779, 5156, 5732, 5733 and 9911 for rev 241), then repacks archive 12 and its reference table.

If it helps, I can open a PR on OpenRune-FileStore. Either:
- a proper fix where the compiler is driven from (if you can point me at where the long var type should be handled), or
- the same post-pass as a `PackCs2` step, as a stopgap until the compiler handles long vars.

Happy to go whichever way you prefer, or leave it with you.
