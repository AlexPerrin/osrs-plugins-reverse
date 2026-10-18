# QA: fix/73-heldu-dispatch-order

- Branch: `fix/73-heldu-dispatch-order`, fetched read-only from server dev #22's clone into `re/upstream-pr/or` as `refs/qa/fix/73-heldu-dispatch-order`
- Commit: `b2f2fbb297dc308a504a2d3d90841d6f824dd33d` (base: upstream `main` `c2492318d`)
- Date: 2026-10-18
- Texts: `batch-3-texts/heldu-dispatch-order.{title,body}`

## Verdict: **incomplete** (stopped for a fresh start)

What ran:
- Fresh clone at the commit (`qa/heldu-dispatch-order-b2f2fbb29` in the QA scratchpad). The builds were queued
  three times: the first two attempts were stopped (a buildq race, then two containers sharing one Gradle volume),
  and the third was stopped at the wrap-up. No build step finished, so there are no build, test or in-game results.
- Code and PR text review (section 4, partial):
  - The diff matches the body: `opHeldU` now tries pair scripts in both orders, then the target's default, then
    the selected obj's default. The firemaking module gets `integration-test-suite` and the new
    `HeldUDispatchOrderTest` (2 tests).
  - True by code: `BurnLogEvents` registers `onOpHeldU(log)` defaults (bow lighting) and the pair is
    `(tinderbox, log)`. So on main, logs used on a tinderbox reach the log default and return silently.
  - Herblore claim, to verify: `FinishedPotionsEvents` registers defaults on every `itemToPotions` key except vial
    of water, pestle and mortar, swamp tar and herbs. So herb on vial is not affected on main. The body says those
    defaults "shadow pairs the same way" but names no affected pair. Find one in game, or reword.
  - Release note: "works again" implies it once worked; on upstream it never did. Minor wording.
  - Not verified: that the new test fails on main (plan: run it with `HeldUInteractions.kt` reverted).

Still to do: section 1 (build, tests, boot), and section 3 in game on the branch and on main. Cases: logs on a
tinderbox, tinderbox on logs, logs on a knife (upstream has no fletching, so expect "Nothing interesting happens."
on both), herb on a vial and the reverse, a secondary on an unfinished potion both ways, and pestle and mortar with
an item both ways. Compare each against OSRS.
