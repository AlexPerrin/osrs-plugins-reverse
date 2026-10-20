# openrune/upstream

What is left here is the server QA tooling: `qa/qa.sh` (the one-container gate and the dev lane, run through
`tools/buildq.sh`), `qa/gate-steps.sh`, `qa/qa-members.sh`, and `QA.md`, which documents them.

The upstream patch queue that used to live here (the numbered patches over the old pin, the batch 1-3 PR texts, the old
`pr-texts/` and `accuracy/` copies, the QA records, `UPSTREAM-PRS.md`, `PR-BATCH-*.md`, the `ISSUE-*.md` drafts,
`id-allocation.json`, `open-batch-1.sh`, `filestore-pr/`) was archived on 2026-10-20 to the fork's `docs` branch under
`archive/openrune-upstream/` (`git -C re/upstream-pr/or show fork/docs:archive/README.md`). Live server material is on
that branch too: STATE.md, `accuracy/`, `pr-texts/`, `qa/` and `server-handoff.md`.

This tooling, `openrune/scripts/`, the image files and `openrune.patch` move to the fork in a follow-up
(task server-tooling-to-fork).
