# QA: fix/74-bankside-view-op

- Branch: `fix/74-bankside-view-op` (`refs/qa/fix/74-bankside-view-op`, read-only fetch from server dev #22's clone)
- Commit: `5015d98efa27df95f610ca6083ccf8bfa345bb9b` (base: upstream `main` `c2492318d`)
- Date: 2026-10-18
- Texts: `batch-3-texts/bankside-view-op.{title,body}`

## Verdict: **incomplete** (stopped for a fresh start)

What ran:
- Fresh clone at the commit. Its build was queued but never started before the wrap-up, so there are no build,
  test or in-game results.
- Code and PR text review (section 4, partial):
  - True by code: on main, `BankInvScript.sideInvOp` has no `Op1` case and its `when` ends in
    `else -> throw IllegalStateException("Invalid main inv op: $op")`. The fix returns early on Op1.
  - True: the bank module's `build.gradle.kts` has no `integration-test-suite` plugin, although
    `src/integration` exists. That supports "No automated test".
  - To verify: the osrs-dumps references (`bankside_drawitem`, `bankside_build`); that a script exception
    disconnects the player on main.

Still to do: section 1, then section 3. In game, as a members player now that the sidecar has the members-world
fix and `qa-members.sh` exists: "Configure" on a rune pouch in the bank's inventory side on main (expect a
disconnect plus the exception in the server log) and on the branch (no disconnect, panel switches). Also "View" on
a looting bag, if upstream can fill one (`LootingBagDropChecks` exists).
