---
name: pr-text-review
description: "Check a PR or release text against its commit: every claim true, upstream CONTRIBUTING's sections present, scope one change. Use before handing over a server branch, and in the upstream and release gates."
---
# PR text review

Upstream's rules: `.github/CONTRIBUTING.md` on upstream `main`; our checklist: QA.md section 4.

1. **Scope.** The diff is one quest, feature or fix, with nothing unrelated in it. Dependencies are stated as
   `Depends:`. There is no open upstream PR for the same content.
2. **Format.** A `feat:` or `fix:` subject, then problem, change, tests, data sources, caveats, release note,
   checklist, and the Claude Code line at the end.
3. **Claims.** Every id, file, test name, result and "matches OSRS" statement is true of the commit. Check each
   one against the diff, the test output and the accuracy spec.

## Report
Each false or missing item, with what it should say. No list of the items that passed.
