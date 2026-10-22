---
slug: server-tooling-to-fork
track: harness
title: Move the server build/deploy tooling to the fork (part 2 of the server split)
prio: P3
role: harness-dev
stage: ready
hold:
  kind: decision
  reason: 'not doable from the harness-dev lane (role limits, not a defect): item 1 needs a push of the new local/ branch to the fork (harness-dev pushes: []; deny_publish denies it), item 2 needs edits
    to .claude/settings.local.json and to non-harness callers (tools/repack.sh, tools/multiclient, tools/osrs-mcp, tools/openrune, .claude/skills, .claude/agents) outside the BR-P4 allow-list, item 3 needs
    a real gate and a real deploy (BR-P2: the daemon only), item 4 would break every caller until 1-3 are done. The v2 task had role orchestrator; the migration re-labelled it harness-dev. Route that fits
    the roles: (a) a server-dev (pushes: [fork]) creates fork local/server-tools from `git subtree split --prefix=openrune` of this repo (history kept) and pushes it; (b) the orchestrator updates the client-side
    callers, the agent/skill files and the settings rules to the fork worktree path; (c) the daemon''s next train proves the gate and the deploy; (d) a harness change flips image.sh/deploy.sh/integrate.py/the
    hooks to the new path and removes the copies (the only part in this lane). Or: re-scope this slug to (d) alone, with (a)-(c) as its deps.'
  since: '2026-10-22T00:36:54Z'
deps: []
branch: null
commit: null
local_only: false
owner: null
instances: []
cost:
  runs: 3
  turns: 12
  minutes: 3.3
  usd: 2.21
created: '2026-10-20T05:00:39Z'
updated: '2026-10-22T00:37:32Z'
---
## Change
Move the server build/deploy tooling to the fork (part 2 of the server split)

## Definition of done
- [ ] openrune/upstream/qa/ (qa.sh, gate-steps.sh, qa-members.sh), openrune/upstream/QA.md, openrune/scripts/ (add_varcs, fix_long_varps, fix_reftable, check_cache, openrune.sh), openrune/{Dockerfile,docker-compose.yml,docker/entrypoint.sh,switch.env,.dockerignore,README.md,LICENSE.OpenRune} and openrune/openrune.patch live on the fork's local/integration-tools branch (or a sibling local/ branch), with history kept
- [ ] every caller updated or given a thin wrapper: tools/buildq.sh, tools/harness/ship.sh, tools/repack.sh, tools/openrune/*.sh, tools/multiclient/*, tools/osrs-mcp/run.sh, .claude/skills/build-test, the server STATE notes, and the permission rules in .claude/settings.local.json
- [ ] proved with one gate (qa.sh gate on integration) and one deploy (rollback tag first, both instances free) from the new location
- [ ] the copies removed from the client repo; openrune/README.md points at the fork

## Log
- 2026-10-21T00:25:19Z migrated from v2 tasks: server-tooling-to-fork
- 2026-10-22T00:24:54Z ready -> dev: dispatched to hd4
- 2026-10-22T00:24:54Z run hd4 started (harness-dev, stage dev, instances none)
- 2026-10-22T00:29:08Z run hd4 ended: ? turns, 0.0 min, $0, exit stopped:the worktree carries the old role hook (false denials on reads); re-dispatched with the fixed hook
- 2026-10-22T00:29:36Z dev -> ready: run hd4 ended without a hand-over
- 2026-10-22T00:30:34Z ready -> dev: dispatched to hd5
- 2026-10-22T00:30:34Z run hd5 started (harness-dev, stage dev, instances none)
- 2026-10-22T00:32:33Z run hd5 ended: ? turns, 0.0 min, $0, exit stopped:worktree cut before the hook fix; re-dispatched on the fixed hook
- 2026-10-22T00:32:52Z dev -> ready: run hd5 ended without a hand-over
- 2026-10-22T00:33:47Z ready -> dev: dispatched to hd6
- 2026-10-22T00:33:47Z run hd6 started (harness-dev, stage dev, instances none)
- 2026-10-22T00:36:54Z hd6 survey (2026-10-21): 18 tracked files under openrune/ (168 commits of history; git-filter-repo is not installed, `git subtree split --prefix=openrune` keeps the history without it). Callers found: harness-side = tools/harness/image.sh (HD_CACHE_SCRIPTS default openrune/scripts), tools/harness/deploy.sh (HD_OPENRUNE_DIR default openrune/ for compose + switch.env), tools/harness/integrate.py:329 (the qa.sh path in the integrator brief), tools/hooks/deny_publish.py (openrune/ compose paths), publish_gate.py (openrune/upstream/qa/*.md), qa_write_scope.py, hd/batches.py docstring; client-side = tools/repack.sh:33 (openrune.sh modulus), tools/multiclient/desktop_login.py:16-22, tools/multiclient/desktop.sh:5, tools/osrs-mcp/run.sh:5-7, tools/openrune/{apk,gradle,http,js5,rsprot-publish}.sh (re/openrune/.spike paths, not openrune/), .claude/skills/build-test/SKILL.md:27, .claude/agents/server-qa.md:27,56, docs/{workflow,multi-client,openrune-port,openrune-spike,harness,harness-v3-handoff}.md; buildq.sh and ship.sh name qa.sh only in comments. The compose file's build context (src/) and Dockerfile are only used by `openrune.sh` builds; deploy.sh runs compose with --no-deps and never --build, so the harness needs only the 4 cache scripts, docker-compose.yml, switch.env and qa.sh at the new location. .claude/settings.local.json does not exist in this checkout.
- 2026-10-22T00:36:54Z hold decision: not doable from the harness-dev lane (role limits, not a defect): item 1 needs a push of the new local/ branch to the fork (harness-dev pushes: []; deny_publish denies it), item 2 needs edits to .claude/settings.local.json and to non-harness callers (tools/repack.sh, tools/multiclient, tools/osrs-mcp, tools/openrune, .claude/skills, .claude/agents) outside the BR-P4 allow-list, item 3 needs a real gate and a real deploy (BR-P2: the daemon only), item 4 would break every caller until 1-3 are done. The v2 task had role orchestrator; the migration re-labelled it harness-dev. Route that fits the roles: (a) a server-dev (pushes: [fork]) creates fork local/server-tools from `git subtree split --prefix=openrune` of this repo (history kept) and pushes it; (b) the orchestrator updates the client-side callers, the agent/skill files and the settings rules to the fork worktree path; (c) the daemon's next train proves the gate and the deploy; (d) a harness change flips image.sh/deploy.sh/integrate.py/the hooks to the new path and removes the copies (the only part in this lane). Or: re-scope this slug to (d) alone, with (a)-(c) as its deps.
- 2026-10-22T00:36:54Z dev -> ready: agent could not finish
- 2026-10-22T00:37:32Z run hd6 ended: 12 turns, 3.3 min, $2.21, exit ok
