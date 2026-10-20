---
name: build-test
description: "Build and test a server branch at a commit (clean build from the shared build cache, spotlessCheck, test, integration, boot) or the client host build. Use before handing a branch over, and for the QA gates."
context: fork
---
# Build and test

## Server branch: CI first (GitHub Actions on the fork)
For a branch based on upstream `main` (every `content/*`/`fix/*` for upstream), the build-and-test evidence comes from
the fork's GitHub Actions `CI` (upstream's own `.github/workflows/ci.yml`: freshCache, buildCache, generateRsa,
`assemble test integration`, boot to ready; plus `Formatting` = spotlessCheck and `Gameval Conflict Check`). It runs in parallel on GitHub's runners (free for the public fork), so it doesn't use a local
build slot.
- It runs on the fork PR's `pull_request` event when `.kt`/`.kts` change. Otherwise (data-only branches, or no fork
  PR yet) start it: `gh workflow run ci.yml -R AlexPerrin/OpenRune-Server --ref <branch>`, and the formatting one with
  `gh workflow run format.yml ...` if it isn't triggered.
- Find the run for the exact commit: `gh run list -R AlexPerrin/OpenRune-Server --branch <branch> --json
  databaseId,headSha,conclusion,workflowName`; wait with `gh run watch <id> -R AlexPerrin/OpenRune-Server` as a
  background command. Record the run URL, head sha and conclusion; a run at another sha doesn't count.
- If CI is red for an infrastructure reason (runner, network, cache download), rerun it once; if it still fails,
  use the local gate below and say why.
- Local `qa.sh gate` is still the gate for `integration` and the live image (they need our patched RSProt,
  `.m2-rsprot`, and the image's fix scripts), and the fallback when Actions is unavailable. `qa.sh gate-run` builds the
  branch server for in-game checks (CI doesn't keep a runnable server).

## Server branch: the local gate
The branch gate (STATE.md on the fork's `docs` branch, "Build, test, deploy"; QA.md section 1). The QA helper is
`openrune/upstream/qa/qa.sh`: `clone <slug> <commit> main`, then `gate <clone>`.
- A fresh `git clone --shared` at the commit (one clone per gate: a shared tree leaks outputs). Copy
  `game.example.yml` to `game.yml` before any run (qa.sh does). Without it the server writes a default `game.yml`
  with `revision: 233`, and `integration` and the boot fail with ComponentDecoder IndexOutOfBounds in every
  suite: infrastructure, not the branch.
- `qa.sh gate <clone>`: one priority ticket in `tools/buildq.sh` (class gate, 2 slots), one container
  (`--cpus=4 --memory=5g`), all steps in order, each its own `./gradlew --no-daemon` run:
  `clean :or-cache:qaCacheToolsClasspath`, `:or-cache:freshCache :or-cache:mergePluginGamevals`,
  `:or-cache:buildCache`, `generateRsa`, `:server:app:installDist` (then `<clone>.logs/runnable`),
  `spotlessCheck`, `assemble testClasses integrationClasses`, `test`, `integration`. `--max-workers=4`
  (unit tests: 2 JVMs at `-Xmx1536m`; integration: 1 at the build's `-Xmx2048m`, since 2 peaked at 4.7-4.9 GB
  of the 5), Gradle `-Xmx1536m`, the Kotlin daemon at
  `-Xmx2g` (it exits after each run, so it is gone when the cache tools, `-Xmx2600m`, or the tests run),
  `--no-configuration-cache` (it misses new modules).
- Build cache ON, remote only: the shared node `tools/gradle-cache.sh` (gradle/build-cache-node, docker network
  `gradle-cache`), init script `tools/gradle/remote-cache.init.gradle.kts`. Compiles of unchanged modules come
  from the cache; **test tasks are never cached**, so every test runs. The OpenRS2 cache download is shared
  by cache id (volume `openrune-dl-cache`); everything built from the branch is built per gate.
- Output in `<clone>.logs/`: one log per step (ending `rc=<n>`), `summary` (rc, seconds, tasks executed /
  from cache per step), `tests` (counts per suite), `status-after`. A failed cache step skips the rest.
- Own Gradle home: QA uses `upstream-pr-gradle-qa`/`-qa2` (whichever is free); a dev gate passes
  `GATE_VOL=<name>-gate-gradle GATE_OWNER=<name>` (a shared home deadlocks on Gradle's journal lock).
- `qa.sh split-build <clone>` is the old run (one uncached container per step) if the cache node is broken.
- `python3 tools/scripts/fix_gameval_conflicts.py --check` (needs freshCache's `max-ids.toml`).
- Boot: `qa.sh start <clone>-run` to "Server ready in" (`qa.sh gate-run <clone>-run` builds it).
- Wait for builds with a background command that notifies on completion, not by polling.

## Server branch: dev builds (evidence only)
`qa.sh dev <owner> <clone> [tasks...]` (the dev lane: buildq class dev, 2 slots, never behind gates). The build
runs in your warm container `qa-dev-<owner>` (made on first use: `--memory=4g`, Gradle home
`<owner>-dev-gradle`, a persistent Gradle daemon `-Xmx1536m` with the Kotlin compiler in-process, the remote
build cache, no `clean`; test JVMs capped at `-Xmx1536m`). Without tasks it compiles, then tests (`:test`, and
`:integration` where the module has it), each module changed since the merge-base with `DEV_BASE` (default
`upstream/main`), plus `buildCache` when packs, raw-cache or gamevals changed; a clone without `.data/cache`
gets the cache steps first (the daemon is stopped for them: the cache tool needs 2.6 GB). Logs in
`<clone>.logs/dev/`. `qa.sh dev-stop <owner>` when you finish (it also stops after 45 idle minutes);
`qa.sh dev-status`. Dev builds are evidence only; the dev's hand-over evidence is CI green at the commit (or a
`qa.sh gate` where CI doesn't apply), and for the upstream gate only server-qa's run at the fixed commit counts.

## Client host
`host/build.sh`, `ninja -C re/build`, then `tools/repack.sh` (7 `telemetry:` lines).

## Report
One line per step from `summary`: pass or fail, the time, and the log path; the `tests` counts. A failure in a
module the branch doesn't touch (OOM 137, a journal lock) is infrastructure: say so and rerun.
