# Harness v3 as built: harnessd

Built 2026-10-20/21 against docs/harness-v3-prd.md (the rules, cited as BR-*) from the first principles in
docs/harness-v3-problem.md. This is the short description of what exists; the PRD stays the contract. v2
(docs/harness.md) is kept only where the PRD says it is already right: the change file format, the hand-over check
rules, the train mechanics and pins, the role files and skills, the record formats, the build queue.

## In one paragraph
A **change** (`harness/changes/<slug>.md`) is the only unit of work; its stage is **observed** from git, CI, GitHub,
docker and the leases every tick and never set by hand. `harnessd` (`python3 tools/harness/hd.py start`) runs the
tick (observe → advance → dispatch → heal → log), spawns **stage executors** (headless `claude -p --agent <role>`,
one change, one stage, a budget), runs **trains** (queue → pins → rebuild → gate → image → deploy) and **re-pins**,
takes every shared resource as a **lease**, parks the two outward actions on an **approval** queue, repairs drift,
and appends every state change to `harness/state/events.jsonl`, which the **dashboard** (`http://127.0.0.1:7790/`)
and the KPIs read. The orchestrator session is a console; the user acts only on approvals and decisions.

## Layout
```
tools/harness/hd.py              the command line (hd ...): changes, daemon, leases, approvals, batches, events, migrate
tools/harness/hd/                the package (one module per concern; each docstring is its spec)
  util events store policy       clock (UTC in, Eastern out), the event log, the records, policy.yaml
  stages observe check           the lifecycle preconditions, external truth, the hand-over check
  leases agents batches ship     resources, stage executors, trains and releases, the client merge
  approvals heal tick daemon     the user's queue, repairs, the tick, the loop + HTTP server
  dashboard kpi migrate          the page and text views, the KPIs, v2 tasks -> changes
tools/harness/image.sh           gated clone -> deploy image (keys only by COPY --from the live image; boot check)
tools/harness/deploy.sh          image -> live (rollback tag, compose up --no-deps, "Server ready in", auto-rollback)
tools/harness/Dockerfile.integration
harness/policy.yaml              tracks, repos, resources, leases, batches, agents (budgets, roles), approvals (BR-P*)
harness/changes/<slug>.md        the store (committed)
harness/state/                   git-ignored: events.jsonl, leases/, runs/, batches/, approvals/, images/, deploys/,
                                 queue.json, live.json, daemon.json, digest.md, view/ (waiting words, last tick)
tools/hooks/task_stop.py         an executor can't end without its result on disk (hand-over, gate verdict, resolution)
tools/hooks/slot_stop.py         an executor can't end holding a lease or an unchecked device backup
tools/hooks/deny_publish.py      agents' limits (unchanged), every denial an event (BR-P3)
tools/device-lock.sh             the v2 command names over `hd lease` (the skills keep working)
.claude/agents/*.md              client-dev, server-dev, client-qa, server-qa, integrator, harness-dev
```

## The lifecycle (stages.py)
`backlog → ready → dev → checked → live → gated → published → merged`; holds `blocked | decision | failed | exception`.
Per track the precondition of each stage is one observable fact (PRD section 4): a client change is live when
`master` contains its commit; a server change when its integration pin equals its commit and the live server runs
an integration that carries it (`live.json`); gated when a PASS record at that commit exists on the fork's `docs`
branch (gate only what is live, BR-S1); published when the OpenRune PR is open, not draft, at the commit; merged
when upstream `main` contains it. `local/*` ends at live. A branch tip past the handed-over commit returns the change
to ready (BR-C2). Deps set and clear `blocked` holds (BR-C4). Unknown facts (gh down) never regress a stage.

## The tick (tick.py), every 60 s
1 reconcile (drift repaired and logged) · 2 reap ended runs; hand-overs get the check (PASS → checked, FAIL → ready
with the items) · 3 checked client/harness changes merge into master; checked server changes join the queue; trains
advance one non-blocking step · 4 a gated image deploys once every instance is drained and leased by the daemon
(BR-R4) · 5 re-pin when upstream main moved · 6 release rule · 7 approvals created for gated changes, executed when
approved, stale when GitHub already shows them done · 8 dispatch ready changes by priority into free slots and free
leases · 9 heal (idle leases, lingering or over-budget agents, dead runs, stale containers, stuck batches,
deadlocks) · 10 the `tick` event with its actions and the stalled changes. Each step is isolated: a failure is an
`error` event and the others run.

## Agents (agents.py, the role files)
`hd dispatch <slug>` or the tick: grant the stage's leases, set the owner, start `claude -p --agent <role>
--permission-mode <policy> --max-budget-usd <policy>` with a brief generated from the change (the slug, the name,
the leases and how to take, heartbeat and release them, the evidence dir, the exact `hd handover|block|fail`,
`hd gate PASS|FAIL`, `hd resolve` commands). On exit the stream-json result's turns, minutes and cost land on the
change (`cost`) and in an `agent end` event; leases held are released; a run alive past its stage or budget is
stopped one tick later (BR-G2). `HD_SPAWN=print` prints the brief for a console session to spawn with the Agent tool.

## Approvals (approvals.py, the dashboard)
Only `upstream` (open, ready, comment-and-push on an OpenRune PR) and `release` (tag + GitHub release), plus
`decision` cards from agents or the migration. Each shows the exact commands and the full text from the fork's
`pr-texts/<slug>.*`, the gate record, the diff range and the caveats. Approve / Deny from the page (or
`hd approve|deny`); the daemon runs it within a tick and records the result. PR descriptions are never replaced.

## Commands you will use
```
hd status            hd list [--track] [--all]     hd show <slug>       hd events [--kind] [--change]   hd kpi
hd start|stop        hd tick --dry-run             hd daemon-status     hd digest
hd approvals         hd approve <id>               hd deny <id> --note "..."
hd lease status      hd batches                    hd train --now       hd resume <batch>
hd new --track server --slug x --title "..." --server-dod [--prio P1] [--deps a,b]
hd migrate [--apply] (v2 tasks -> changes, once)   hd lint   hd check <slug>
```
Tests: `python3 -m unittest discover -s tools/harness/tests` (a test per business rule: test_hd_*.py).

## Open decisions (PRD section 13)
Milestone dates for the forecast; the notification channel (the digest is written to harness/state/digest.md);
the dashboard host (local, as built); the order of the branch convergence (each acc/*, fin/* branch is a `decision`
card after the migration: fold into the PR branch, your OK per PR).
