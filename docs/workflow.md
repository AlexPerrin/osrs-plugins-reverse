# Workflow: client and server tracks

Two repos, two tracks. The orchestrator (main session) runs both, does every push to GitHub and every
deploy, and decides; agents never push, post or deploy.

| | Client track | Server track |
|---|---|---|
| Repo | `AlexPerrin/osrs-plugins-reverse` (this repo) | `AlexPerrin/OpenRune-Server` (fork of `OpenRune/OpenRune-Server`) |
| Code | `native/`, `host/`, APK repack (`tools/repack.sh`, `patch_telemetry.py`, `patch_server.py`), `sidecar/` (today `server/`) | OpenRune content, engine fixes, our local-only server pieces |
| Ships as | Tagged APK releases (`client-vX.Y`) | Upstream PRs, plus our live test server |
| Developer | Client dev (gen N) | Server dev |
| QA | Client QA (`qa/client-<version>.md`) | Server QA (`openrune/upstream/QA.md` checklist → moves to the fork) |

Shared workspace tooling stays in this repo: `tools/buildq.sh` (build queue), `tools/device-lock.sh`,
`tools/fork.sh`, `tools/multiclient/`, `tools/progress.py`, and the test environment (the compose stack that runs
the live server and sidecars, pinned to a fork commit).

## Server track

### Branches on the fork
- `main`: mirrors upstream `main` exactly. Only `tools/fork.sh sync` (fast-forward) moves it. Nothing of ours
  goes here, so upstream PRs and syncs stay clean.
- `content/<slug>` and `fix/<NN>-<slug>`: one self-contained feature or fix each, off `main`, with
  upstream ids. These are what get PR'd upstream. Shared infrastructure gets its own small branch, so features
  don't stack on each other (UPSTREAM-PRS.md lists any real dependencies).
- `local/*`: our test-only pieces that never go upstream: the Android/mobile protocol support, LOCAL_AUTH hooks,
  test commands (`::pos`, `::heal`, `::mapanim`...), deploy files. Kept small and separate by topic.
- `integration` (the fork's default branch): `main` + every `content/*` and `fix/*` that isn't merged upstream
  yet + every `local/*`, merged in a scripted, recorded order (`integration.txt` on the branch). It's rebuilt, not
  hand-edited: `scripts/build-integration.sh` recreates it from `main` and the list. Our live server builds from it.
- `docs`: server docs (port notes, server handoff, accuracy specs, QA records, PR texts, UPSTREAM-PRS.md).

### Flow for a feature or fix
1. **Spec first.** `accuracy/<slug>.md`: each mechanic with its source (wiki section, cache, Mod post), the
   planned behaviour, caveats, guesses, and the tests. The orchestrator reviews it before coding starts.
2. **Develop** on `content/<slug>` off `main`, in the server dev's own clone. Tests for each rule. Build with
   `tools/buildq.sh`.
3. **Fork PR.** The orchestrator pushes it and opens the PR on the fork (`tools/fork.sh`, no confirmation).
4. **QA**, by the QA agent, on the branch's own server (upstream `main` + branch, spare ports): uncached build,
   tests, spec check, scripted protocol scenarios, and in-game checks on desktop RuneLite where a plugin renders
   something. Recorded as PASS or FAIL in `qa/<slug>.md` on `docs`. A new commit voids the record.
5. **Upstream PR.** On a PASS, the orchestrator opens it (asks the user). It goes into `integration` at the same
   time, so the live server gets it.
6. **Upstream merge.** `fork.sh sync`, drop the branch from `integration.txt`, rebuild `integration`, deploy.

Pushes to a branch that already has an open upstream PR change that PR, so they ask the user.

### Live server
- It builds from a pinned `integration` commit. The orchestrator deploys boot-checked images when both devices
  are free, with a rollback tag (standing approval).
- Live ids equal upstream ids. Saved player data is migrated with an SQL map (old id → new id) when ids change, so
  there is one id space only.

## Client track

### Flow
1. **Develop** on short branches `client/<topic>` in this repo, merged to `master` after a passing device
   check. Small fixes may go straight to `master`.
2. **Device slots.** Take the free device for a stated time (see device lock). At the end, return it to the
   normal state.
3. **Release.** When a set of features is ready, the client QA agent runs the release QA, and the orchestrator
   tags `client-vX.Y`. A GitHub release is published only with the user's confirmation, and never with the
   Jagex APK or anything its licence forbids.

### Client QA (`qa/client-<version>.md`)
- A regression pass on the device: core plugins and pinned hub plugins, scripted through the devtools bridge where
  possible, with the plugin-host table updated.
- Network QA (docs/app-telemetry.md): required for every release and for every network-relevant change.
- Desktop A/B for plugins that draw things.
- Sign-in (LOCAL_AUTH) and the privacy patches present in the build (`repack.sh` + `patch_telemetry.py` log).
- Done by the QA agent, not the client dev.

## Shared rules
- **Builds** go through `tools/buildq.sh <owner> -- docker run ...` (2 slots, 4 GB per container,
  `-Pkotlin.compiler.execution.strategy=in-process`).
- **Devices**: `tools/device-lock.sh`. Server restarts reserve both devices.
- **Accuracy**: server behaviour must match the official server; divergences are caveats or fixes.
- **Privacy**: no client ever contacts Jagex or RuneScape servers. Every APK goes through the telemetry patch.
- **Commits**: pathspec-only, as Alex Perrin, with the Claude co-author line; PR texts end with the Claude Code
  line.
