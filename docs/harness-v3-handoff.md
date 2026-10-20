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

## 5. State at hand-off

(Updated at session end.)
