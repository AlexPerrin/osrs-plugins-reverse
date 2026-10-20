# Harness v3 handoff

From the harness-v2 orchestrator session (2026-10-20, session a450a085, "osrs-plugins-reverse-52") to the Fable session
that builds harness v3. Draft written mid-session; the "State at hand-off" section is updated when the session ends.

Read with: docs/harness.md (v2 as built), docs/integration-pipeline.md (the approved integration design, phases 0-1
done), and the three reviews in `re/evidence/harness-v2-review/`: `kpi.md` (measurements), `session-a450a085.md`
(this session's timeline and user rules), `sessions-earlier.md` (v1 and the v1->v2 build).

## 1. Did v2 make things faster?

| Area | Verdict | Evidence (kpi.md) |
|---|---|---|
| Build / gate time | **No change from v2.** The gain came from 60b0ed5 (remote build cache, one-container gate, warm dev lane) in the v1 window: full gate ~26 min (60-120 min with 3 concurrent) -> ~9-10 min warm. Branch gates: median 530 s in v2 vs 593 s late v1, same step times. Integration gates 690-725 s (bigger integration step). Earlier numbers: gate 35-50 min -> ~17 min cold / 7.6-12 warm; installDist 644 s -> 91 s cold / 23 s warm; dev-lane compile ~10 s. | gate logs, STATE.md |
| Harness loop | **Probably faster, unprovable**: v1 kept no task records. v2 (4 h sample): 3 tasks/h handed over, claim->handover median 0.5 h, ship <=0.1 h, deploy +0.3 h. 7 of 18 hand-over checks failed first time. | task logs |
| Agent cost | **Better.** Role agents end at ~120-150k tokens (v1 210-280k); median wall time server-dev 108->38 min, server-qa 73->15 min. Fork agents for integration/image cost ~3x (median 433k, max 557k). | transcripts |
| Friction | **Worse.** Denials 0.9/h -> 7/h (mostly "Only the orchestrator does pushes" x9 before the hook fix, Self-Modification classifier x7). User messages about approvals or blocked/idle agents 0.34/h -> 1.75/h. Lock "held by" denials 0.6/h -> 2.8/h. | transcripts |

## 2. Key takeaways from v2

1. **What worked:** the file task store as the single source of truth; generated briefs; the deterministic hand-over
   check and ship step (after their gaps were fixed); approval files; role hooks; worktree subagents for harness
   work; tests (0 -> 60 in one day, which caught real bugs); the Ending rule (agents exit after hand-over).
2. **The orchestrator is still on the critical path** for every hand-back (check, ship, unblock, dispatch, relay).
   The user spent turns resolving things a driver should: stale approvals after a restart, stale tasks, lock jams,
   agents lingering. `orchestrate.py run` (the autonomous loop) was never used for real.
3. **Hooks were the biggest friction source.** Pattern matching on command text misfired: the bare word "upstream"
   (blocked every fork PR edit), in-command variables, `$(...)` substitutions, `--help`, heredoc text, worktrees made
   in the same command. Each fix needed a test. v3 should resolve targets (remote URLs, repos) rather than match
   words, and log every denial.
4. **The auto-mode classifier blocks harness self-edits** (Self-Modification) unless settings allow them; the user
   added `Edit(tools/hooks/**)`, `Edit(tools/harness/**)` and autoMode rules. Editing settings drops the session out
   of auto mode, which then prompts for everything. Plan settings changes up front and batch them.
5. **Integration work through fork agents is too expensive** (400-560k tokens each, three rebuilds in one day) and
   races with ship. The approved fix is the scripted train pipeline (docs/integration-pipeline.md).
6. **Shared instances need a real lease model.** Locks were held idle; one was reclaimed on a wrong guess (branch
   server named without the agent suffix); an agent then stopped another agent's desktop client without the lock.
   Lock times print in local time (EDT), which caused a wrong "overdue" call.
7. **Status semantics matter.** `shipped` counted as closed, so client checks could run before the deploy; fixed in
   phase 0 with a `live` status. QA tasks and local-only branches did not fit the hand-over check; fixed.

## 3. User rules learned (standing; full quotes in session-a450a085.md)

- Approval only for OpenRune upstream actions and client releases. Fork, integration, docs, deploys, own-repo pushes
  go ahead. A prompt anywhere else is a hook bug to fix.
- Before anything that may prompt: one plain-words line saying what it does and that it will prompt; run it alone.
- Prefer the gh CLI subcommands over `gh api` (gh 2.45 breaks `gh pr edit` on OpenRune: upgrade gh).
- Agents exit once their task is handed over/blocked/failed; the orchestrator stops any that linger.
- Hold an instance lock only while using it. No screenshots unless the user asks (data tools and logcat).
- Keep the test server on current upstream main (re-pin whenever main moves).
- Client priority: the user's own RuneLite plugins (project-user-plugins memory). Stretch goals: GPU plugin and
  117 HD, considered individually.
- No stacked upstream PRs; fork-first; accuracy against the official game (RSProx recordings beat the wiki).

## 4. Requirements for v3 (from the reviews)

1. **Finish the integration pipeline** (phases 2-5 in docs/integration-pipeline.md): image.sh, deploy.sh with
   all-or-none locks and auto-rollback, the driver's tick/drain, repin detection, a small `integrator` agent for
   exceptions only.
2. **Make the driver run the loop**: reap -> check -> ship/enqueue -> train -> deploy -> mark live -> unblock ->
   dispatch, without the orchestrator's chat turn. The orchestrator handles approvals, decisions and exceptions.
3. **Event log** (`events.jsonl`, append-only, UTC): every claim, handover, check, gate, ship, train, deploy, lock
   acquire/deny/release/reclaim, hook and classifier denial, PR update/ready; agent id, type, task id, tokens,
   duration, exit reason on every agent end. Gate summaries gain cold/warm, cache-hit %, queue wait, UTC times.
4. **Hooks resolve, never match words**; one shared parser (tools/hooks/shellparse.py is the start) with a test per
   real false positive; denials logged.
5. **Lease-based instance locks**: holder heartbeat or attached resource (branch server / client pid), automatic
   expiry when idle, orchestrator reclaim with evidence, `desktop.sh`/client commands refusing when another agent
   holds the lock (a first attempt was reverted untested), UTC in all output, branch servers named by agent.
6. **Session robustness**: role files and hooks that take effect without a session restart; MCP servers that
   reconnect (the devtools MCPs were down most of the day; agents fell back to bridge scripts).
7. **Settings as code**: the permission and autoMode rules the harness needs, reviewed once, so harness work does
   not stall on Self-Modification blocks.

## 5. Findings

### 5a. Hang-ups (work stopped or waited on something a harness should handle)

| When (UTC unless noted) | Hang-up | Duration | Cause | Resolved by |
|---|---|---|---|---|
| 10-19 10:13-18:51 (local) | Agents idle "holding, waiting for your direction", 4 approvals queued | 8.5 h | approvals only surface in chat; nothing pings the user or continues other work | user returning |
| 10-18 23:52-10-19 01:31 (local) | "Everything is waiting": build queue stalled | ~1.5 h | gates 35-50 min, too few slots, WSL memory | build speed-up agent, WSL 15.5->20 GB |
| 10-17, 10-18 | Orchestrator auto-compactions at ~967k and 576k tokens; "three days through several compactions" | each a context reset | one long-lived orchestrator relaying every agent round | hand-written docs/orchestrator.md + manual restart |
| 10-20 03:13 | Restart: reap filed 3 stale approvals for actions already done | user turn | approvals not checked against GitHub state | user ("fix stale"), phase-0-era reap fix |
| 10-20 ~03:20-04:40 | QA/dev fork actions blocked ("Only the orchestrator does pushes", the bare word "upstream") | ~1 h, 4 agents | hook matched words, not targets | hook rewrite + tests; user approved one-off pushes |
| 10-20 ~03:40 | Client docs-only change and a QA capture bounced by the hand-over check | 2 user turns | check assumed every client task builds and merges | manual merge, then check fix |
| 10-20 03:50-05:40 | sd26 kept running after its task shipped ("Reading the CI conclusion report") | 1 h 46 min | agent waited on CI after hand-over; no exit rule | TaskStop; Ending rule added |
| 10-20 04:10-04:20 | Auto mode exited after a settings edit; every command (even a git clone for the gate) prompted | until the user re-enabled auto | settings edits drop auto mode | user switched back |
| 10-20 04:20-04:40 | Deploy image blocked: copying the live login key denied (credential handling) | ~20 min + user turn | no sanctioned path for the key | user-added autoMode rule; `COPY --from` |
| 10-20 05:30-05:40 | Desktop lock "deadlock": sd28 held the desktop while others waited | ~10 min | only the holder could free; idle-hold allowed | `device-lock.sh reclaim/revoke` (added under pressure) |
| 10-20 05:40 | sd28 stopped sq29's desktop client without the lock | sq29's run lost | client scripts don't check the lock | reported by sd28; guard attempt reverted |
| 10-20 ~06:03-06:06 | sq29's cannon test commands (::item spawns, teleport, logout) drove sq30's desktop2 client | sq30's Rub/ignore checks redone | the devtools bridge is addressed by port, not tied to the lease; an orchestrator message offered sq29 desktop2 as an alternative | both agents pinned to one instance each by message. Root cause found by sq29: the session scratchpad is shared by all subagents; sq29's `scratchpad/dt.sh` was overwritten by sq30's desktop2 version. Fix: per-agent scratch dirs (brief and role files); v3: per-agent workspaces and leases bound to the bridge |
| 10-20 05:26-? | Clue-tier image ready but undeployable while any instance is held | 30+ min | deploy needs all three instances; no drain/priority | waiting (phase 3 adds drain) |
| all day | devtools MCP servers not connected; agents used bridge scripts with WSL interop timeouts | constant | MCP lifecycle tied to session start | agents' workarounds |

### 5b. Changes made on the fly (during live work, often under a blocked agent)

- Publish hook rewritten five times in one day: word match -> URL resolution -> in-command variables -> `$(...)` ->
  `--help`/heredoc -> worktrees created in the same command; tests grew alongside (0 -> 60).
- Hand-over check: docs-only client exemption, QA tasks, spec name variants, `local/*` branches, gated vs done for
  QA PASS (a regression the first fix introduced and review caught).
- ship.sh: four bugs found on first real use (sh process substitution, section placement, rerere into the worktree
  git dir, plain push instead of a lease); build-integration.sh's rerere path fixed on the fork.
- Status model: `live` added; `shipped` stopped counting as closed.
- Locks: `reclaim`/`revoke` added mid-incident; a reclaim was then judged on a wrong signal (container name) and
  lock times were misread (EDT vs UTC).
- Rules added to every brief and role file mid-run: Ending, lock-only-while-in-use, no screenshots. Running agents
  had to be messaged individually because role files load at spawn.
- Settings: Edit/autoMode allow rules for harness edits and for the deploy key; `--build` deploy rules removed.
- Integration: re-pin and two image builds done by full-context fork agents; then the pipeline was designed and
  phases 0-1 built and merged the same afternoon.
- Memory: consolidated by another process mid-session (several notes merged or dropped, including the screenshot
  rule), then re-added.

### 5c. Core issues a major revision must identify and solve

1. **The orchestrator is the scheduler.** Every hand-back, ship, unblock, dispatch, grant and relay needs its chat
   turn, so its context fills (compactions), its attention gates throughput, and idle periods stall everything.
   v3 needs a driver that runs the loop itself and an orchestrator that only decides.
2. **Rules don't persist.** "No approval for fork actions" was given on 10-18 12:14, 10-18 22:23, 10-19 20:42 and again
   twice on 10-20; "explain before approval" three times. Rules live in chat, memory and prose that agents load at
   spawn. v3 needs rules as data (policy file) that hooks, briefs and the driver read, versioned and tested.
3. **Policy is enforced by text matching.** Hooks guessed intent from command strings and failed both ways (false
   denials on fork work, near-misses on real risks). v3 needs targets resolved (repo, remote, branch, instance) and a
   single policy decision point with a test for every past incident.
4. **No model of shared resources.** Devices, desktops, build slots, the live server and integration are each locked
   differently (mkdir locks, flock, buildq tickets, nothing). v3 needs one lease service: holder, purpose, attached
   process/container, heartbeat, expiry, reclaim with evidence, UTC, history.
5. **State drifts from reality.** Stale approvals, stale tasks, leftover grants, "shipped" vs deployed, fork vs local
   docs, memory vs disk. v3 needs reconciliation: the driver checks GitHub, git and docker state before acting and
   repairs or flags drift.
6. **Agent lifecycle is unmanaged.** Agents linger, can't receive rule changes, and their cost isn't tied to tasks.
   v3 needs spawn-to-exit tracking (task id, tokens, duration, exit reason), enforced exit, and rule updates that
   reach running agents.
7. **Expensive general agents do scripted work.** Integration, image and deploy ran as 400-560k-token forks. v3
   should script every repeatable step and keep agents for judgement (conflicts, accuracy, failures).
8. **The harness can't change itself safely.** The classifier blocks self-edits unless settings allow them, and
   settings edits exit auto mode. v3 needs a planned path for harness changes (allow-listed paths, worktree +
   tests + orchestrator merge) decided up front with the user.
9. **No measurement.** v2's effect on speed could not be proven: no event log, no queue/lock history, no agent cost per
   task. v3 must instrument first (events.jsonl) so v4 can be judged.
10. **Approvals wait silently.** An approval in chat blocks until the user reads it (8.5 h once). v3 needs approvals as
    a queue the user can act on asynchronously (and notification), with all other work continuing.

## 6. Stretch goals: what is desirable from a new harness

Beyond fixing section 5. **The user's priorities for v3 (2026-10-20): 1. runs overnight without the user,
2. a live dashboard, 3. self-healing** (items 1, 3 and 4 below). The others are optional; v3 picks their scope with
the user. Section 5c's core issues are the means to these three: overnight runs need the driver-run loop, durable
rules, lease-based resources and reconciliation; the dashboard needs the event log; self-healing needs
reconciliation and leases.

### 6a. Harness

1. **Runs overnight without the user** (user priority 1). The driver works the queue for hours: dispatch, check, ship, train, deploy,
   unblock, re-pin, with every step logged. The user returns to a summary, not a backlog. Only upstream actions and
   client releases wait, as approvals.
2. **Async approvals and notifications.** Approvals form a queue the user can act on from anywhere: an Artifact page
   or a phone push, with the exact command and text, and approve/deny. Work that doesn't depend on them keeps going.
   A blocked or failed task, or an idle instance, pings once.
3. **A live dashboard** (user priority 2). Tasks per track, running agents and their cost, instance leases, the build queue, the live
   server (integration, image, rollback), upstream PRs and their review state, and the KPI trends from the event log.
   One page instead of `status` dumps in chat.
4. **Self-healing** (user priority 3). Stale grants, lingering agents, dead branch servers, a disconnected MCP, a failed deploy (auto
   rollback), a drifted task store: detected and repaired by the driver, with an event, before anyone notices.
5. **Cost-aware agents.** A token and time budget per task and role; the smallest agent that fits (scripts first, a
   fresh role agent next, a fork only when the full context really matters); model choice per role (the earlier
   review found Opus and Fable equal on reversing, so cost and speed decide); cost per task in the event log.
6. **Elastic instances.** More than one Android device and desktop (desktop2 exists; more BlueStacks instances or
   emulators), on-demand branch servers named by agent, leases that scale. Parallel client checks instead of one
   device slot.
7. **Faster builds again.** Test-impact selection (gate only the modules a change touches, a full gate per train),
   a warm pool of gate containers, cache-hit tracking, and client host builds with timings and caching.
8. **Upstream automation.** Maintainer review comments become tasks automatically. Fork CI runs on every push to a
   PR branch. Re-gates are queued when a PR head moves. Merged PRs drop out of integration on the next train.
   PR texts are kept in sync with their commits.
9. **Policy as code with tests.** One versioned policy file (who may push where, what needs approval, which
   instances a role may use, screenshot rule, exit rule) read by hooks, briefs and the driver; a test per past
   incident; changes reviewed once with the user, taking effect without restarting sessions.
10. **Golden-task evaluation.** A small set of replayable tasks (a server fix, a client plugin port, a gate) to compare
    harness versions and models on time, tokens and first-pass check rate before rolling a change out.
11. **Reproducible runs.** Every train, image and deploy records the exact commits, tools version and settings, so any
    live state can be rebuilt or bisected.
12. **Session continuity.** No more compaction-driven restarts: the orchestrator's state lives on disk (task store,
    events, policy), so a fresh session picks up in one command, and long sessions hand over automatically.

### 6a-kpi. The KPIs that matter (ranked; all from events.jsonl, UTC)

Product progress (what the harness exists to move; user definitions, 2026-10-20):
- **Client: RuneLite compatibility on mobile.** RuneLite API implemented (661/866, 76%), core plugins running on
  Android (89/90), Plugin Hub plugins running (84/109), from tools/progress.py.
- **Server: parity with the official OSRS server** (100% = identical copy). Not measured yet: task
  server-parity-metric builds tools/harness/server_parity.py (OSRS content from cache and wiki vs the integration
  tree, upstream main vs integration side by side).
- docs/progress.md (tools/harness/progress_report.py) reports both; task counts there are workflow throughput only.

Harness headline (one per user priority):
1. **Unattended hours**: wall time the loop kept making progress with no user message. Target: a full night.
2. **Throughput**: tasks reaching `live` (server) or master (client) per hour, and **lead time** claim -> live
   (median, p90). Upstream: PRs updated or marked ready per day.
3. **Stall time**: total minutes any ready task or any agent waited on something the harness owns (a lock, a slot,
   a check, a ship, a deploy, an approval it could have skipped). Self-healing drives this to zero.

Deadlocks and stalls (the issue the user named):
4. **Deadlock incidents**: cycles or idle holds where a waiter can't proceed because a holder isn't using the
   resource (lock held with no attached process, a train waiting on a deploy waiting on a lock). Count per day.
5. **Time to detect** and **time to recover** each incident (target: detected within one tick, recovered without
   the user).
6. **Idle-under-lock minutes** per instance, and **lock wait** per waiter.
7. **Lingering agents**: agents alive after their task left in-progress, and minutes lingered.

Quality and cost:
8. **First-pass hand-over check rate** (v2: 11 of 18), and gate first-pass rate.
9. **Interventions**: user messages per day that fix the harness rather than direct work (v2: 1.75/h), split into
   approvals that weren't needed, blocked agents, stale state.
10. **Denials per hour** by source (hook rule, classifier category); false-positive rate after review.
11. **Tokens and minutes per task** by role and agent type (fresh vs fork), and per train/deploy.
12. **Build**: gate time cold/warm, queue wait, cache-hit %; client build time. Kept for regression only (v2 showed
    the harness doesn't move it; the cache did).

Dashboard: 1-3 as tiles with trends, 4-7 as an incident list, 8-12 as tables; each links to its events.

### 6b. Product goals the harness should carry

- **GPU plugin** and **117 HD** on the Android app, separately (client-gpu-plugin-feasibility,
  client-117hd-feasibility): spikes first, then porting tasks from each plan.
- **The user's own RuneLite plugins** (project-user-plugins): 22 of 25 hub plugins start; the rest need user decisions
  (WikiSync, AFK Marks Canifis, Quest Voiceover use new hosts), client setters (Animation Smoothing, Camera), the
  host WorldEntity API (Sailing, Barracuda Trials), and 11 server content tasks (Barbarian Assault, Guardians of the
  Rift, Trouble Brewing, Gnome Restaurant, Sailing, drift nets, Forestry, kebbit tracking, Stealing Artefacts,
  Canifis rooftop, Perilous Moons).
- **Upstream contribution flow** that keeps pace with the maintainers: fast re-gates, small single-change PRs, no
  stacking, accuracy against the official game.

## 7. The integration and upstream change model (and the breaking changes v3 should make)

### 7a. Finding: one feature, many branches

Each feature lives on three or four fork branches, with nothing tying them together (2026-10-20):

| Feature | Fork branches | Integration (live server) runs | Upstream PR branch |
|---|---|---|---|
| Cannon | acc/cannon, content/cannon, fin/cannon | fin/cannon | content/cannon (#354); acc/cannon is the gated rewrite |
| Genie | acc/genie, content/genie, fin/genie | fin/genie | content/genie (#355); acc/genie holds today's 7 accuracy fixes |
| Goblin Diplomacy | content/goblin-diplomacy, -829, -unstacked, fin/goblin-diplomacy | fin/goblin-diplomacy | content/goblin-diplomacy (#367) |
| Witch's Potion | 4 branches | fin/witchs-potion | content/witchs-potion (#368) |
| Ground item timers | fix/76-obj-add-timers and fix/ground-item-despawn-times (same fix) | both, until the first train conflicted | fork PR #23 only (fix/76 dropped 10-20, tools ff7e6eec8) |

Consequences: the test server does not run what goes upstream (the genie fixes are on acc/genie, live runs
fin/genie); gates ran on commits the server never ran; every gate, rewrite or unstack created a branch name; only
the orchestrator's memory knew which branch was current, which is why integration work fell to full-context fork
agents. The first scripted train exposed one case directly (fix/76 vs the despawn fix, resolved by dropping fix/76;
resolution notes in re/evidence/int-20261020-1/resolution.md).

### 7b. The model v3 should implement

1. **One change, one branch.** A feature or fix has exactly one branch, the one its upstream PR uses
   (`fix/<slug>`, `content/<slug>`). Integration merges that branch at a pin. Rewrites, merges from main and gate
   fixes are new commits on it, never new branches. `local/<slug>` for what never goes upstream. No `fin/*`,
   `acc/*`, `-829`, `-unstacked`.
2. **A change record**, keyed by the slug: branch, upstream PR, spec, PR text, gate records, pin, live commit,
   upstream state, caveats. Today one feature is scattered over hand-linked tasks (spec-genie, gate-acc-genie,
   fix-acc-cannon-text, wait-354-355-undraft); in v3 tasks are the stages of a change.
3. **One lifecycle the driver advances**, generating each stage's task and brief from the record:
   dev (spec, code, fork CI) -> queued -> in a train (rebuild, gate, image) -> live (client checks unblock) ->
   upstream gate (QA, at the live commit) -> gated (approval to open/update the PR: the user) -> in review
   (maintainer comments become fix tasks on the same branch; each new commit re-enters at queued) -> merged upstream
   (the next re-pin drops the branch from integration). An agent never has to work out where a change stands.
4. **Gate only what is live.** The upstream gate runs on a commit integration already carries, so the in-game
   check is on the real test server and "gate PASS" means "what we tested is what we send".
5. **No fork agents for integration.** Trains are scripts (docs/integration-pipeline.md phases 2-5); the only
   agent is a small integrator for exceptions, briefed from the change record and the stop output.

This feeds the user's three priorities: overnight runs (the driver advances changes without history), the dashboard
(one row per change and its stage), self-healing (clear invariants: pins match records, gate commit is live,
merged means dropped).

### 7c. Breaking changes: considered 2026-10-20, all handed to v3

The user chose to hand all of these to v3 ("Place all items in handoff") rather than apply them in v2. Only the
fix/76 drop (an instance of item 1) was done.

Considered for "now" (cheap while little is in flight):
1. **Converge to one branch per change**: fold acc/* into content/* for cannon and genie (merge commits; updates
   #354/#355 upstream, needs the user's OK per PR), point integration at the PR branches, archive fin/*, acc/*,
   -829, -unstacked. Changes what the live server runs: one train, gate and deploy per step. Order: cannon and
   genie (their gates ran 10-20 on acc/* commits that are not live), then Goblin Diplomacy, Witch's Potion, the rest.
2. **Gate only what is live**: enforced in the hand-over check and the gate brief (needs item 1 first).
3. **`change:` id on every task** (the branch slug), back-filled once; additive, the key for 7b's record.
4. **Event log from day one** (`events.jsonl`, append-only, UTC) from task.py, the checks, ship, trains, locks,
   deploys; additive; the baseline section 6a-kpi needs.
5. **UTC everywhere, branch servers named per agent** (`qa-or-server-<agent>`): small output/name breaks; both
   caused wrong calls on 10-20 (an "overdue" lock that wasn't, a reclaim judged on container names).

Better as one clean break in v3:
6. **Change record + driver state machine** (7b), replacing hand-linked tasks.
7. **Policy as data**: one versioned file read by hooks, briefs and the driver, replacing rules spread over hooks,
   role files and memory.
8. **Lease-based instances**, replacing device-lock.sh; the devtools bridge and client scripts check the lease
   (10-20: an agent stopped another's client, another's commands drove a third agent's client).
9. **Settings as code**, and the server tooling on the fork (task server-tooling-to-fork).

Keep: the task store, the hand-over check, the train scripts, role agents, the tests.

## 8. State at hand-off (2026-10-20 ~06:35 UTC)

What v2 shipped: docs/v2-shipped.md. Live progress: docs/progress.md (`tools/harness/progress_report.py`).
Task store: `tools/harness/orchestrate.py status` (117 tasks, lint clean). No new work was dispatched after the
user's "We will have to wait for v3".

### Live
- Test server: integration **e27ff4d08** on upstream main 829b9477d (image `osrs-openrune:integration-e27ff4d` = `:241`,
  deployed 2026-10-20 06:46 UTC / 2:46 AM EDT, train int-20261020-1): everything below plus the clue Check tier text
  and the ground item despawn timers (fix/76 dropped as superseded). Ready in 9.6 s, 0 errors.
  Rollback: `osrs-openrune:rollback-e5b18a8` (the previous live, integration e5b18a87b), then `rollback-5cc4733`.
- Fork default branch: `integration`. Pins: `integration.lock` on fork `local/integration-tools` (ff7e6eec8 + the
  train's commit).
- Unblocked by this deploy (ready for v3): hub-clue-steps-tier, hub-ground-items-despawn (client checks on the device).

### Deploy procedure (used for train int-20261020-1; to be scripted in pipeline phases 2-3)
Gate the train's integration commit (`QA_ROOT=re/harness/qa openrune/upstream/qa/qa.sh clone integration <sha>`, then
`qa.sh gate <clone>`); stage the image context without key files and apply the cache fixes (openrune/scripts:
add_varcs, fix_long_varps, fix_reftable, check_cache on data/cache/LIVE); build from the Dockerfile in
re/evidence/int-20261020-1/Dockerfile-e27ff4d (key files and game.yml `COPY --from` the live image); compare key
hashes; boot-check privately; then, with device, desktop and desktop2 held:
```
docker tag osrs-openrune:241 osrs-openrune:rollback-<live sha7>
docker tag osrs-openrune:integration-<sha7> osrs-openrune:241
(cd openrune && docker compose --env-file switch.env up -d --no-deps openrune)   # never --build
```
Wait for "Server ready in", free the locks, log "Live <date> ..." on the included tasks, `task.py migrate-live`.

### Gates finished at hand-off
| Gate | Verdict | Next |
|---|---|---|
| gate-acc-cannon (sq29), acc/cannon bc243d141 for #354 | **PASS** (fork docs ec733bc60). Break and repair seen in game; decay, Nulodion's replacement, part-built decay and pick-up of a broken cannon covered by the integration tests only (the PR text says so). Break came at 23 min 19 s real time = the code's 2500 ticks on a host running 0.559 s/tick. | #354: fold acc/cannon into content/cannon as a merge commit, update the description from fork docs pr-texts/cannon.upstream.body (4c75a5850), mark ready (user's OK given). Done 2:55 AM EDT: content/cannon -> 9c08b42ca (merge commit, tree = acc/cannon), description and comment posted; ready waits for upstream CI at 9c08b42ca (if not marked ready at hand-off, run `gh pr ready 354 -R OpenRune/OpenRune-Server` once CI is green). |
| gate-acc-genie (sq30), acc/genie 3ccb86bd8 for #355 | **FAIL** (fork docs 970ba88ec): leave on the next call tick after Talk-to (RSProx 1718); lamp XP must equal the message | fix-genie-gate-findings (P1), then re-gate; #355 stays draft |

Both gated acc/* commits the live server doesn't run (integration carries fin/cannon, fin/genie): section 7a.

### Upstream (OpenRune/OpenRune-Server)
Open for review: #362, #366, #367, #370 (in review), #372, #373. Drafts: #354, #355 (gates above), #368 (Witch's
Potion; task gate-witchs-potion ready). 17 of our PRs merged so far.

### Ready queue left for v3 (top items)
- Server: despawn-copy-owner-id (on the despawn branch), server-parity-metric (server progress), fix-bankside-view-op-refresh,
  gate-witchs-potion, members-general-stores, spec-* dev checks, 11 content tasks for the user's plugins (backlog).
- Client: hub-clue-steps-tier and hub-ground-items-despawn (after the deploy), client-gpu-plugin-feasibility,
  client-117hd-feasibility, sidecar-ge-digest.
- Harness: integration-phase-2 (ready) .. phase-5, server-tooling-to-fork, two cleanups.
- User decisions open: WikiSync / AFK Marks Canifis / Quest Voiceover (new hosts), client setters (Animation
  Smoothing, Camera).

### Where things are
- Design and review: docs/harness-v3-handoff.md (this), docs/integration-pipeline.md, re/evidence/harness-v2-review/.
- Memory (auto-loaded): approvals only for upstream + releases, explain before prompting, gh CLI, agents exit,
  locks, no screenshots, progress definitions, user plugins, stretch goals, harness v3 priorities.
- Settings: `.claude/settings.local.json` allows Edit on tools/hooks, tools/harness, docs/harness.md (also in
  worktrees) and has autoMode rules for harness edits and the deploy key; the `--build` deploy rules are removed.
- Untouched by request or rule: `qa-dev-sq24` container and `sq24-dev-gradle` volume (from an earlier session);
  BlueStacks kept running (user).
