# Integration pipeline ("trains"): design

Approved by the user 2026-10-20. Goal: routine integration work (ship, rebuild, gate, image, deploy, repin) runs as
scripts in the harness; an agent is spawned only for exceptions, with a small brief, instead of full-context forks
(400-550k tokens each today). Tasks: integration-phase-0 .. integration-phase-5.

## Bugs to fix first (phase 0)
1. `task.py` counts `shipped` as closed, so a client task whose blocker is only shipped (not deployed) can be
   dispatched early. Add a `live` status; remove `shipped` from CLOSED; unblock dependents on `live`.
2. `build-integration.sh` merges branch tips, not the handed-over commits (phase 1: pins).
3. `.claude/settings.local.json` allow rules permit `docker compose ... --build` and `openrune.sh deploy` (which
   builds); the real deploy is `up -d --no-deps openrune`, never `--build` (orchestrator fixes the settings).

## Scripted vs agent steps
Scripts: integration.txt edits (sections, drops, local-only moves), rebuild with recorded resolutions and a lease
push, gameval check, live names check (`names-check.py`), gate, image, key-hash check, boot check, deploy, marking
tasks live, routine repin (`fork.sh sync`, drop branches merged upstream). Agent (new `integrator` role, fresh
context, ~1-2k-token brief from `integrate.py`, ~50-150k tokens): unrecorded conflict, gate failure, image/boot
failure, a branch superseded rather than merged, a gameval clash. It records resolutions, never pushes `integration`,
never deploys, and is stopped by the driver once the train leaves `needs-agent`.

## Commands and files
`tools/harness/integrate.py` (queue, lock, trains), `image.sh`, `deploy.sh`, `names-check.py`;
`orchestrate.py integrate {status|enqueue <task>|build [--now]|tick|repin-check|resume <train>}`,
`orchestrate.py image <train>`, `orchestrate.py deploy [<train>]`.

## Batching and serialization
- `ship.sh` (server) only enqueues: checks fork/<branch> == the handed-over commit, appends to
  `re/harness/integration/queue.json` under a flock. No rebuild at ship time.
- One integration lock (`re/harness/integration/.lock`) for anything that changes integration.txt, `integration` or
  `local/integration-tools`.
- A train drains the queue: one integration.txt commit, one rebuild, one lease push, then gate -> names check ->
  image in the background. Starts when the oldest entry is >= INTEGRATION_DEBOUNCE_MIN (10) min old, or `--now`.
  The newest gated image is deployed; older trains' tasks go live with it.
- Pins: `integration.lock` (branch sha lines) beside integration.txt on `local/integration-tools`;
  `build-integration.sh` merges the pinned sha; a tip that moved without a hand-over keeps its pin (warned).

## Image (`image.sh <gated-clone> <train>`)
Preconditions (gate rc=0, commit matches); stage server/ + data/ without key files (refuse if present); cache fixes
(add_varcs, fix_long_varps, fix_reftable, check_cache) on the staged copy; checked-in template
`deploy/Dockerfile.integration` with `ARG LIVE_IMAGE` pinned to the live image id, key files and game.yml only by
`COPY --from`; key-hash compare printing only match/MISMATCH; private boot check (no port, tmpfs postgres, trap rm);
record `re/harness/integration/images/<sha7>.json`. Allow rule `Bash(tools/harness/image.sh*)`.

## Deploy (`deploy.sh <train>`)
Image record ok; take all three instance locks or none (exit 75 busy); rollback tag; retag; compose up
`--no-deps openrune`, never `--build`; wait for "Server ready in", auto-rollback on timeout; free locks in a trap;
write `live.json` (status prints it); tasks -> `live`, unblocks run. Orchestrator only (deny_publish denies agents);
the driver deploys when the instances are free, with a capped drain mode.

## Task model
One task per train (`int-<yyyymmdd>-<n>`, track orch, role integration; skipped by dispatch) with `includes`,
`integration`, `tools`, `image`, `stage`. New status `live`; local-only tasks go live -> done.

## Tests
test_integrate.py, test_deploy.py (PATH stub docker, LOCK_DIR override in device-lock.sh), test_image.py,
hook and settings checks, task.py live/unblock.

## Phases
0 bugs + deny rules + LOCK_DIR (half a session); 1 queue/lock/trains/pins (~400 LOC); 2 image.sh + names check
(~250); 3 deploy.sh + driver tick/drain + live.json (~250); 4 repin-check + integrator role + resume (~250);
5 server tooling from the fork after server-tooling-to-fork (small).

## Risks
A bad batch blocks a train (the integrator drops one branch and re-runs); the key (never echoed or copied; pin the
live image id); drain starving agents (cap it); squash-merge detection misses (to the integrator, never silently
dropped); gameval fixes on branches with open upstream PRs (approval); disk (prune old images and QA clones).
