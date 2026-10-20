# Harness: the loop as files and scripts

The harness runs two tracks (client: RuneLite plugins in the official OSRS Android app; server: the OpenRune fork
contributed upstream) through one loop, **trigger → analyse → build → verify → ship**, with as few human and
orchestrator turns on the path as possible. docs/workflow.md defines the tracks, roles, gates and skills; this
document is the machinery that drives them and why it is shaped this way. It follows Anthropic's "Harness design
for long-running application development": separate the generator from the evaluator, make the evaluator
skeptical and concrete, keep the planner on deliverables not implementation, move state into files so a session is
disposable, and remove each piece as soon as the model no longer needs it.

## What was slow in the first harness

The first harness (up to 2026-10-19) was a chat session as orchestrator, four role agents, hooks for the gates, and
state spread across docs/backlog.md (tables), two STATE docs ("Next tasks") and docs/orchestrator.md (a queue
rewritten by hand). Measured against the loop, the orchestrator's turn was on the critical path six times per task:

| Step | Who waited on whom | Cost |
|---|---|---|
| Device slot | the dev asked "main" for a grant mid-task | an agent turn lost per slot, and the orchestrator's attention |
| Spec review (server) | the dev stopped until "main" reviewed the spec | the review rarely changed the spec; the stop always cost |
| Hand-over | a chat report of paths; "main" re-read it, merged by hand | reports pulled logs into the orchestrator's context; evidence lived in session scratchpads that vanish |
| Blockers | written in both STATE docs' "Next tasks" by hand | drift between the two; nothing checked that a blocker was cleared |
| Ship (merge, integration, deploy) | a hand procedure each time | errors of omission; no record of which check was done |
| Restart | docs/orchestrator.md rewritten from memory | the queue in that doc was already stale by the time it was read |

The orchestrator session also grew until compaction, then rebuilt its context from a hook that printed both STATE
docs in full (about 400 lines) after each compaction.

## The pieces

```mermaid
flowchart LR
  tasks[("harness/tasks/*.md<br/>one task, one file")]
  driver["orchestrate.py<br/>status · next · dispatch · reap · run"]
  dev["dev agent<br/>(client-dev / server-dev)"]
  check["handover-check.sh<br/>deterministic evaluator"]
  ship["ship.sh<br/>master / integration"]
  qa["QA agent<br/>(release / upstream gate)"]
  appr[("harness/approvals/<br/>for the user")]
  user([User])
  tasks --> driver -- "brief + grants" --> dev -- "task.py handover" --> tasks
  driver -- "handover" --> check -- PASS --> ship --> tasks
  check -- FAIL --> tasks
  ship -- "server: qa task" --> tasks
  driver -- "qa task" --> qa -- "gated / failed" --> tasks
  driver -- "gated" --> appr --> user
```

| Piece | File | Does |
|---|---|---|
| Task store | `harness/tasks/<id>.md` via `tools/harness/task.py` | One file per deliverable: YAML frontmatter (track, status, prio, role, owner, instances, branch, commit, evidence, blockers) plus `## Task`, `## Definition of done` (checkboxes) and a dated `## Log`. The single source of truth for both tracks; `ready`, `status`, `lint`, `metrics` are computed from it |
| Brief | `task.py brief <id>` | The whole spawn prompt: the id, the agent's name, the granted instances, the evidence dir, how to hand over. Role text stays in `.claude/agents/*.md` |
| Driver | `tools/harness/orchestrate.py` | `status` (dashboard), `next` (what fits the free slots), `dispatch` (name the agent, grant instances with `reuse`, claim, print the brief, or `--run` a headless `claude -p --agent <role>`), `reap` (finished runs → check → ship → QA task → approval), `run` (the loop) |
| Hand-over check | `tools/harness/handover-check.sh <id>` | The evaluator between the dev's claim and the ship step, deterministic: definition of done ticked; branch at the commit; evidence dir present; client: repack log with 7 `telemetry:` lines, a screenshot or logcat, Network QA when `network: true`; server: branch on the fork at the commit, off upstream main, `accuracy/<slug>.md` on docs, fork PR recorded, fork CI `success` at exactly that sha |
| Ship | `tools/harness/ship.sh <id>` | Client: `--no-ff` merge into `master` with the standard message, push to origin main. Server: append to `integration.txt`, rebuild `integration` with `build-integration.sh`, push; prints the gate and deploy commands (they reserve instances, so they stay separate) |
| Evidence | `re/evidence/<id>/` | Durable, in the main checkout, git-ignored. Screenshots, logcat extracts, the repack log, accuracy results. Replaces per-session scratchpads |
| Instances | `tools/device-lock.sh` + `tools/instances.tsv` | One lock per test instance (`device`, `desktop`; add rows for more BlueStacks instances). `grant <who> <min> reuse` at dispatch: the agent takes and frees it for the whole task without asking |
| Build queue | `tools/buildq.sh`, `openrune/upstream/qa/qa.sh` | Unchanged classes and memory guard; queue and lock dirs moved from a session scratchpad to `re/harness/{buildq,qa}` |
| Stop hooks | `tools/hooks/task_stop.py`, `slot_stop.py` | An agent can't finish while its task is still `in-progress`/`specced`, while it holds a lock, or before the device restore check passed |
| Publish gates | `tools/hooks/publish_gate.py`, `github_scope.py`, `deny_publish.py` | Unchanged: upstream PR needs the QA record at the commit; tags need the release record; agents can't push upstream, deploy, grant or write progress |
| Approvals | `harness/approvals/<id>.md` | The only thing that reaches the user: an upstream PR, a release, a deploy decision. Written by the driver when a task reaches `gated`; the orchestrator runs the action in the interactive session (the `ask` rules still prompt), then `task.py done <id>` and removes the file |
| After compaction | `tools/hooks/session_state.sh` | Prints the dashboard (`orchestrate.py status`) and pointers, not the STATE docs |

### Task lifecycle

```
backlog → ready → in-progress → (specced, server) → handover → shipped → qa → gated → done
                     ↓ blocked_by …          ↑ FAIL (check)        ↓ FAIL (gate)   ↓ waiting-user
                   blocked ──────────────────┘                   failed → fix task
```

- `backlog`: a deliverable without a definition of done yet (the roadmap). `ready`: dispatchable once every
  `blocked_by` task is `done`, `shipped`, `qa` or `gated` (live or past dev).
- A **blocker** between tracks is an edge, not prose: the client dev runs `task.py new --track server ...` for the
  server task and `task.py block <mine> --by <it>`. `task.py done <server-task>` flips the client task back to `ready`.
  `lint` rejects dangling edges and cycles.
- A **hand-over** is `task.py handover` with branch, commit, evidence and summary after every definition-of-done
  box is `[x]`. The check and the ship step need no chat. A check FAIL sets the task back to `ready` with the failing
  items in the log; the next dispatch (a fresh agent, same role) fixes exactly those.
- **QA** runs only at the two outward gates (docs/workflow.md "Gates"). A server ship creates `qa-<id>` for
  `server-qa` automatically; a client release task is created by the orchestrator when a release is due. A gate PASS
  sets `gated` and files an approval; a FAIL sets `failed` with the record path in the log, and the orchestrator
  creates the fix task for the dev (same slug, `-fix`).

### Who does what now

| | Before | Now |
|---|---|---|
| Orchestrator (interactive) | granted slots, reviewed specs, read reports, merged, rebuilt integration, deployed, rewrote the queue | dispatches (`orchestrate.py dispatch` or `run`), decides what `next` says to decide, runs approvals and deploys, writes decisions in docs/orchestrator.md |
| Dev agent | asked for slots, reported in chat, evidence in its scratchpad | takes its granted instances, hands over on disk, evidence in `re/evidence/<id>/`; the server spec is committed and work continues (`specced`) unless a decision is needed |
| QA agent | reported a verdict in chat | records the verdict, sets `gated`/`failed` |
| User | approved upstream PRs, releases, restarts | the same, from `harness/approvals/` and the ask prompts |

## Running it

**Interactive (default).** The orchestrator session:
```
tools/harness/orchestrate.py status                 # where everything is
tools/harness/orchestrate.py next                   # what fits the free slots
tools/harness/orchestrate.py dispatch <id>          # grants, claims, prints the brief
  → Agent(subagent_type=<role>, isolation=worktree for client roles, prompt=<the brief>)
tools/harness/orchestrate.py reap                   # after agents finish: check, ship, QA task, approvals
tools/harness/orchestrate.py approvals              # what the user must decide
```
`reap` also picks up hand-overs from in-session agents (no run record): any task in `handover` without a check gets
one. Server ships print the integration gate and deploy commands; the orchestrator runs them when both instances
are free (standing approval for deploys with a rollback tag).

**Headless (autonomous loop).**
```
tools/harness/orchestrate.py run --interval 120     # reap, dispatch while slots are free, repeat; Ctrl-C to stop
tools/harness/orchestrate.py run --once             # one pass
```
`dispatch --run` starts `claude -p --agent <role> --permission-mode $HARNESS_PERMISSION_MODE --output-format stream-json --verbose` (every tool call is a line in `re/harness/runs/<name>.log`) (default `auto`)
with the brief, in a worktree under `re/harness/wt/<name>` for client roles, logs under `re/harness/runs/`. In
`-p` mode nobody answers permission prompts: anything outside the allow rules is denied, so before the first
headless run extend `.claude/settings.local.json` for the agents' routine commands (the `fewer-permission-prompts`
skill reads the transcripts and proposes the list). The role hooks still deny pushes upstream, deploys and grants,
and the stop hooks still hold an agent until its task is on disk and its instance is restored. The loop never
touches upstream or releases: those are approval files for the interactive session.

Slots: `HARNESS_MAX_CLIENT` (1, one Android instance), `HARNESS_MAX_SERVER` (2), one QA companion per track. A
second BlueStacks instance is a row in `tools/instances.tsv`, its MCP server in `.mcp.json`, and
`HARNESS_MAX_CLIENT=2`.

## Design notes (what the article says, and what it means here)

- **Generator and evaluator are separate, and the evaluator is skeptical and concrete.** Self-evaluation is lenient;
  here the dev's check is kept (the user's velocity decision: no QA before `master`/`integration`) but its *claims*
  are verified mechanically by `handover-check.sh`, which only passes on evidence that exists (the repack log's
  telemetry lines, a screenshot, CI `success` at the sha, the spec file). The two outward gates keep an independent
  LLM evaluator (QA companion) with a record the publish hooks parse.
- **Encode quality as gradable criteria weighted toward what the model does poorly by default.** Here: OSRS accuracy
  with cited sources (the accuracy spec and its RSProx comparison), one change per PR, privacy (Network QA, telemetry
  assertions), device-verified behaviour. These are definition-of-done items and gate checklists, not prose.
- **Sprint contracts.** Every task has a definition of done before dispatch (`lint` and `dispatch` refuse one
  without). The server spec is the contract for content work; it is committed and the dev continues (`specced`)
  rather than waiting, because the review almost never changed the spec but always cost a turn.
- **Context.** The orchestrator's context is for decisions. Agents report one line; results live in task files;
  after compaction the hook prints a dashboard, not documents. Agents hand read-heavy lookups to read-only subtasks
  (role files, "Subtasks").
- **Disposable sessions.** Everything a restart needs is on disk: the task store, run records, approvals,
  `docs/orchestrator.md` for decisions only. A fresh orchestrator runs `orchestrate.py status` and continues.
- **Remove what isn't load-bearing.** `task.py metrics` gives cycle times and rework counts per task from the logs.
  If a check never fails over a month, drop it; if a human step never changes the outcome (the spec review did
  not), make it asynchronous, as done here.

## Not built yet
- `build-integration.sh` refusing a branch without green CI at its commit (today `handover-check.sh` is the only
  guard before `ship.sh`).
- A headless run of a real task: the driver's `--run` path has been exercised only up to the `claude -p` command.
  Run one client task headless with the orchestrator watching before trusting `run` unattended.
- The release path: `release-vX.Y.Z` client-qa tasks are created by hand; the tag and GitHub release stay in the
  interactive session (publish_gate.py).
