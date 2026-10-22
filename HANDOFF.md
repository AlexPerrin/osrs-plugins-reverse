# HANDOFF: start here (harness v3 session)

Updated 2026-10-20 8:45 PM EDT by the v3 build session. **Harness v3 (`harnessd`, "hd") is built and
tested; it is not started yet.** Read docs/harness-v3.md (what exists), then this file. Times for the user are
always US Eastern; never report UTC to them.

## 0. v3 in one minute
- The store is `harness/changes/<slug>.md` (117 changes, migrated from v2's tasks by `hd migrate --apply`; v2's
  `harness/tasks/` is kept as history and is no longer read). State that is observed, not remembered, lives in
  the git-ignored `harness/state/` (events.jsonl, leases, runs, batches, approvals, live.json).
- First commands:
```
python3 tools/harness/hd.py status                 # progress, approvals, every change with why it waits
python3 tools/harness/hd.py tick --dry-run         # what one tick would do (reads git, gh, docker; writes nothing)
python3 tools/harness/hd.py lease status           # the leases (device, desktop, desktop2, integration, live-server, ghidra)
python3 tools/harness/hd.py events --limit 30      # the event log, local time
python3 -m unittest discover -s tools/harness/tests   # 169 tests, must pass
```
- Agents are watched in Claude Code Agent Monitor (http://127.0.0.1:4820/, `cd ~/tools/Claude-Code-Agent-Monitor
  && npm start`); the page on 7790 is the project tracker. docs/harness-v3.md "Watching agents".
- To run it: `hd serve --detach` (the tracker page, its own process, http://127.0.0.1:7790/; never restarted with
  the loop) and `hd start --interval 60` (the tick loop: trains, deploys, agents, approvals); `hd stop`,
  `hd serve-stop`, `hd daemon-status`, `hd digest`. A code change needs only `hd stop && hd start`. The first real start is the user's call (it spawns paid agents and takes
  the instances).
- What the first real ticks will do (from the dry run at 8:40 PM EDT): move `57-gameframe-fullscreen-map`,
  `sheep-shearer` and `edgeville-general-store-stock` to `published` (open upstream PRs); return `cannon`,
  `genie` and `goblin-diplomacy-unstacked` to `checked` (the live server runs `fin/*`, not these branches; each
  carries a `decision` hold: fold into the PR branch, the user's OK per PR); dispatch one client-dev
  (`hub-ground-items-despawn`), two server-devs (`ground-item-despawn-times` further work, then by priority) and
  one server-qa (upstream gates for live branches without a record), within the slots and free leases.
- Open: the acc/* and fin/* convergence decisions (one `decision` card each on the dashboard), the spec-missing
  `failed` holds on five live content branches (a dev writes the spec before the gate), the notification channel
  (the digest is written to harness/state/digest.md), milestone dates for the forecast.

## 1. The project in five lines
- RuneLite plugins running inside the official OSRS **Android app** (native/ hooks + host/ Java plugin host), in this
  repo (AlexPerrin/osrs-plugins-reverse, `master` -> `origin main`).
- A test server: **OpenRune** (OpenRune/OpenRune-Server upstream), developed on the fork **AlexPerrin/OpenRune-Server**
  (local clones under `re/upstream-pr/or` and `re/agents/<name>/or`). The fork's `integration` branch is what the live
  test server runs.
- Progress, as the user defines it: **client = RuneLite compatibility on mobile** (`tools/progress.py`);
  **server = parity with the official OSRS server**, 100% = an identical copy (not measured yet: task
  `server-parity-metric`).
- Work runs through **harness v3** (`tools/harness/hd.py`, docs/harness-v3.md): a change store, a daemon that
  observes, advances, dispatches, heals and logs, leases, trains, approvals and a dashboard. v2's files
  (`tools/harness/orchestrate.py`, `task.py`, `harness/tasks/`) are history.
- Your job: start the daemon when the user says so, act on decisions and exceptions, relay the user's approvals
  (the dashboard or `hd approve`), and keep building against docs/harness-v3-prd.md.

## 2. First commands (v2 era; v3's are in section 0)
```
git pull --ff-only origin main
python3 tools/harness/hd.py status
python3 -m unittest discover -s tools/harness/tests   # 169 tests, must pass
git -C re/upstream-pr/or fetch -q origin main && git -C re/upstream-pr/or log --oneline -1 origin/main
```
If upstream main moved past 829b9477d, a re-pin is due (the user's rule: keep the test server on current upstream).

## 3. Read, in this order
1. `docs/harness-v3-prd.md`: **the PRD** (the model in its simplest terms, every business rule, the dashboard:
   progress, status, approvals; the daemon pivot; acceptance criteria). v3 is built and accepted against it.
2. `docs/harness-v3-problem.md`: the problem definition for v3 (the goal per track, first principles, the
   lifecycle of a change as a state machine with tools and dependencies, the driver tick, the invariants to test).
3. `docs/harness-v3-handoff.md`: the full handoff (v2 KPIs and verdict, takeaways, findings, stretch goals and
   KPIs, the change model, breaking changes, state at hand-off). Section 8 is the state.
4. `docs/integration-pipeline.md`: the approved train design; phases 0-1 done, 2-5 to build.
5. `docs/harness.md` and `docs/workflow.md`: v2 as built.
6. `re/evidence/harness-v2-review/` (`kpi.md`, `session-a450a085.md`, `sessions-earlier.md`): evidence behind the
   handoff.
7. `docs/v2-shipped.md`: what v2 delivered. `CLAUDE.md`: project rules. Memory is auto-loaded.

## 4. State right now
- **Live test server**: integration `e27ff4d08` on upstream main `829b9477d`, image `osrs-openrune:integration-e27ff4d`
  (= `:241`), live since 2:46 AM EDT 10-20. Rollback: `osrs-openrune:rollback-e5b18a8`.
- **Nothing running**: no agents, no locks, no grants, no stray containers. BlueStacks is running and idle (keep it
  running: user). The device and both desktops are free.
- **Upstream PRs**: ready for review #354 (cannon), #362, #366, #367, #372, #373; in review #370. Drafts: #355
  (Genie: gate FAIL, fix task below), #368 (Witch's Potion: gate task ready). 17 of ours merged.
- **Top of the ready queue** (`orchestrate.py next`):
  - P1 `hub-ground-items-despawn`, `hub-clue-steps-tier`: client checks on the device, unblocked by the last deploy.
  - P1 `fix-genie-gate-findings`: two accuracy fixes on `acc/genie` (leave on the next call tick after Talk-to,
    lamp XP equal to its message), then re-gate (`gate-acc-genie`), then #355 leaves draft.
  - P2 `integration-phase-2` (image.sh), `server-parity-metric`, `despawn-copy-owner-id`, `gate-witchs-potion`,
    `fix-bankside-view-op-refresh`; client `client-gpu-plugin-feasibility`, `client-117hd-feasibility`.
- **Known debt** (handoff section 7a): each feature lives on 3-4 fork branches (`fin/*`, `acc/*`, `content/*`, ...);
  the live server runs `fin/*` while upstream PRs use `content/*`. v3 converges to one branch per change.

## 5. Priorities (the user's, in order)
1. **Runs overnight without the user** (driver runs the loop; only upstream actions and client releases wait).
2. **A live dashboard** (product progress tiles, KPIs from an event log, one row per change).
3. **Self-healing** (stale state, idle locks, lingering agents, failed deploys repaired automatically).
Suggested order of work: event log first (`events.jsonl`, so v3 can be measured), then pipeline phases 2-3 (image,
deploy) and the driver tick, then the change model and leases, then the dashboard on top of the event log.

## 6. Rules you must follow (also in memory and CLAUDE.md)
- **Approvals**: ask only for OpenRune upstream actions (push to a PR branch, open/edit/comment/ready/close) and
  client releases. Fork pushes, fork PRs, integration, docs, deploys (standing approval), own-repo pushes go ahead.
  A prompt anywhere else is a hook bug: fix the hook, with a test.
- **Before anything that may prompt**: one plain-words line saying what it does and that it will prompt; run it alone.
- **gh CLI** subcommands, not `gh api`; exception: `gh pr edit` fails on OpenRune with gh 2.45 (retired projects
  field): use `gh api -X PATCH` for edits and say so, or upgrade gh.
- **Deploy**: boot-checked image, all three instances held, rollback tag first, `compose up -d --no-deps openrune`,
  never `--build`; tell the user. The login key comes only from the live image by `COPY --from`; never print or copy it.
- **Agents**: exit once handed over/blocked/failed; you stop any that linger. Locks only while in use; per-agent
  scratch dirs; no screenshots unless the user asks. Only you deploy, grant, reclaim and push integration.
- **Upstream**: no stacked PRs, one change per PR, match the official game (RSProx recordings beat the wiki),
  accuracy divergences listed as caveats, attribution lines on commits and PR texts.
- **Never**: contact Jagex/RuneScape servers, read or modify the user's own RuneLite (`C:\\Users\\Alex\\.runelite`),
  `::empty` the tester account, stop processes you didn't start.
- Commits: pathspec-only, authored as Alex Perrin <44126061+AlexPerrin@users.noreply.github.com>, ending with the
  Co-Authored-By line for your model.

## 7. Open user decisions
- Client: allow WikiSync, AFK Marks Canifis, Quest Voiceover (each contacts a new host)? Add client setters for
  Animation Smoothing and Camera?
- v3: which breaking changes (handoff 7c) to make first, and when; the user handed all nine to v3.

## 8. Gotchas
- Lock times print in local time (EDT); branch servers from `qa.sh start` use fixed names unless renamed per agent.
- The auto-mode classifier blocks harness self-edits outside the allowed paths; editing settings drops the session
  out of auto mode (everything prompts until the user re-enables it). Plan settings changes up front.
- WSL to Windows interop (adb.exe, desktop bridge) times out intermittently (`UtilAcceptVsock accept4 failed 110`):
  retry. The devtools MCP servers often don't connect; `tools/devtools_mcp.py --call` and the desktop bridge script work.
- A long-running orchestrator compacts; keep state on disk (task store, this file, the handoff) and restart early.
