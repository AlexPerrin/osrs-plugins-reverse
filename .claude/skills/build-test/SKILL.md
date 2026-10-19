---
name: build-test
description: "Build and test a server branch at a commit (clean uncached build, spotlessCheck, test, integration, boot) or the client host build. Use before handing a branch over, and for the QA gates."
context: fork
---
# Build and test

## Server branch
The branch gate (STATE.md on the fork's `docs` branch, "Build, test, deploy"; QA.md section 1). The QA helper is
`openrune/upstream/qa/qa.sh` (`clone <slug> <commit> main`, then `build <clone>`).
- A fresh `git clone --shared` at the commit, with its own Gradle home volume (a shared one deadlocks).
- Copy `game.example.yml` to `game.yml` in the clone before any run (qa.sh does). Without it the server writes a
  default `game.yml` with `revision: 233`, and `integration` and the boot fail with ComponentDecoder
  IndexOutOfBounds in every suite: infrastructure, not the branch.
- Cache steps (`freshCache`, `buildCache`) need `-Xmx2600m`; the default heap runs out of memory.
- Every container through `tools/buildq.sh <owner> -- docker run ...`, `--cpus=4 --memory=4g`, `--max-workers=2`,
  `-Xmx2g`, `-Pkotlin.compiler.execution.strategy=in-process`, `--no-build-cache --no-configuration-cache`.
- Separate runs: `clean :or-cache:freshCache :or-cache:mergePluginGamevals`, `:or-cache:buildCache`,
  `spotlessCheck`, `assemble`, `test`, `integration` (1 worker), then boot to "Server ready in".
- `python3 tools/scripts/fix_gameval_conflicts.py --check` (needs freshCache's `max-ids.toml`).
- Wait for builds with a background command that notifies on completion, not by polling.

## Client host
`host/build.sh`, `ninja -C re/build`, then `tools/repack.sh` (7 `telemetry:` lines).

## Report
One line per step: pass or fail, the time, and the log path. A failure in a module the branch doesn't touch
(OOM 137, a journal lock) is infrastructure: say so and rerun that step.
