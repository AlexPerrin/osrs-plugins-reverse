# Orchestrator handoff

> **2026-10-20 ~06:35 UTC: harness v2 session handed off.** Start here: docs/harness-v3-handoff.md (section 8 is the
> state at hand-off: what is live, the ready-to-deploy train int-20261020-1, the two running gates and their next
> steps). What v2 shipped: docs/v2-shipped.md. Progress: docs/progress.md.


Rewritten at each milestone so a fresh orchestrator session can take over without the old conversation. Read with
CLAUDE.md, `tools/harness/orchestrate.py status` (tasks, runs, instances, approvals), docs/client-state.md and the
fork's STATE.md (`git -C re/upstream-pr/or show fork/docs:STATE.md`). Last rewrite: 2026-10-19 (harness v2: the queue moved to harness/tasks).

## How the orchestrator works
- Delegate: anything beyond a couple of quick commands goes to a subagent (typed roles from .claude/agents/, or a
  one-off general agent for tooling/research). Relay verdicts and decisions; don't pull logs into the main context.
- Restart only to avoid repeated compaction (not on a schedule): rewrite this file first, then a fresh session takes over. Subagents don't survive a session;
  their work is on disk (clones under re/agents/<name>/, docs worktrees, records on the fork's docs branch), so a
  new orchestrator re-dispatches with `orchestrate.py next` / `dispatch`.
- Gates (2026-10-19): server and client devs ship on their own check (server: accuracy spec incl. RSProx + green
  fork CI → integration/live; client: built + changed path tried on the device → master). QA runs only the server
  upstream gate and the client release gate. Older notes below that say "internal PASS" mean the dev's check.
- Upstream: nothing reaches OpenRune/* without the user's OK; print the exact action first; append comments, never
  edit an original PR description; merge main into PR branches rather than force-push.

## Live
- Server: integration e5b18a87b on upstream main 829b9477d (image osrs-openrune:integration-e5b18a8, live 2026-10-20 04:44 UTC), rollback osrs-openrune:rollback-5cc4733.
- Client: v0.1.0 released; dev builds via tools/release-dev.sh.
- Build system: one-container QA gate + shared build cache + dev lane (qa.sh gate / dev), buildq classes.

## Queue, blockers, approvals
In the task store since 2026-10-19 (docs/harness.md): `tools/harness/orchestrate.py status` (dashboard), `next`
(what fits the free slots), `dispatch <id>` (grants + brief), `reap` (hand-over check → ship → QA task → approval),
`approvals` (what the user must decide; also `task.py list --track orch --status waiting-user`). Decisions that are
not tasks go below.

## Restart to the new harness (2026-10-19)
The session running the old harness (osrs-plugins-reverse-73) finishes its in-flight agents; nothing new is spawned
the old way. Their work is mirrored in the task store as `in-progress` with owners sd23, sd24, sd25, sq24, sq25
(`task.py list --owner <name>`). When each finishes, record its result with `task.py handover|set|log` (branch,
commit, evidence path, PR URL) so the new driver takes it from there. Then start a fresh session here:
`tools/harness/orchestrate.py status`, `reap` (hand-over checks and ship steps), `next`, `dispatch <id>`. The only
hand-written state left is this file (decisions) and the STATE docs (what is live, gotchas).

**Old session done (2026-10-19 ~23:00):** all its agents (sd23, sd24, sd25, sq24, sq25) have finished, and their
results are in the task store. Unpushed: sd23's 513144d3d and a44a3daa0; dev docs commits (sd23 de3dac3fa,
sd24 aa924079b, sd25 ce5e5c8bb plus uncommitted edits); QA records (sq24 docs-publish 60f5e23ea, sq25 8c59858aa).
Master 87f3fa9 (desktop2) is unpushed. Hooks in agent role files only take effect in a new session.
Done with the user's OK (2026-10-19 ~23:20): #362 pushed 113b4bbac and commented (still draft, title kept);
#366 pushed 01da94f17, commented, ready for review; #371 pushed a695b985f and commented (draft; its title is
stale); #351 closed.
Waiting on the user (each upstream item needs an OK):
- (#362 marked ready for review with the user's OK.)
- Lookout ladder: gated at b87819158. It opens together with the quest-only #367 update, after the #367/#368
  upstream gate.
- Members' general stores: approved as a separate follow-up PR, task members-general-stores (ready). #371 closed; its work continues in #372 (same branch, a695b985f), opened with the user's OK and marked ready.
Agents exit when done (user, 2026-10-20): the brief and the role files end with an "Ending" rule (stop background jobs, containers and locks, then end). After each hand-back the orchestrator checks ListAgents and stops (TaskStop) any agent still running whose task has left in-progress.
User decisions this session: keep the live test server on current upstream main (re-pin whenever main moves, 2026-10-20); no stacked PRs; fork changes need no approval, only upstream ones; devs and QA do
their own fork GitHub work; the orchestrator only coordinates; #362 option (a); #371 = all general store
data and handlers, draft until ready; read-heavy lookups go to subtasks.

## Standing decisions
- Updated versions go onto the existing upstream PRs (#354-#357, #366-#368) after their gates, each push confirmed at
  the time; overlapping content (Doric #334, farming #231, specials #313, fletching #262) goes upstream as draft PRs
  only, referencing the other PR (user, 2026-10-18).
- Server spec review is asynchronous: the dev commits the spec and continues (`specced`); veto through the task log.
- Deploys: boot-checked image, rollback tag first, both instances free (standing approval).

## Locks and resources
Device: free (runs gen 46's poison-sprite-override build; the tester has a fish barrel in slot 15 until the server's barrel Destroy fix). Desktop: free, RuneLite on the live target. BlueStacks Pie64 must be started on
Windows after a reboot/WSL restart (`HD-Player.exe --instance Pie64`). WSL has 20 GB (C:\Users\Alex\.wslconfig).
