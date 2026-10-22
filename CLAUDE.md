# osrs-plugins-reverse

**This repo is the client and its APK releases only.** Server code and tooling go to the OpenRune fork; the harness that
runs both tracks is its own repo, **AlexPerrin/osrs-harness** (`~/osrs-harness`; read its HANDOFF.md first). The
shims here (`tools/harness/hd.py`, `tools/hooks/*.py`, `tools/device-lock.sh`, the `harness` symlink) exec it.

RuneLite plugins running inside the official OSRS Android app (native/ hooks plus the host/ Java plugin host),
tested against an OpenRune test server (openrune/; upstream branches in re/upstream-pr/or, the fork
AlexPerrin/OpenRune-Server).

How work is organised (tracks, agents, gates): docs/workflow.md; the machinery that drives it (harness v3: the
change store, the daemon, leases, trains, approvals, the dashboard): ~/osrs-harness/docs/harness-v3.md. Current state, read first:
- changes, leases, batches, approvals: `python3 tools/harness/hd.py status` (one change = one file in harness/changes/)
- client: docs/client-state.md
- server: STATE.md on the fork's `docs` branch (`git -C re/upstream-pr/or show docs:STATE.md`)

History (docs/client-handoff.md; server: server-handoff.md and archive/ on the fork's `docs` branch) is read only when needed.

## Roles
- Both tracks run one loop: a change goes backlog → ready → dev → checked → live → gated → published → merged
  (docs/harness-v3-prd.md); the daemon `harnessd` (`hd start`) advances it from observations.
- The orchestrator (the main session) is a console: decisions, exceptions, and relaying the user's OK for upstream
  actions and releases (the dashboard's approvals, or `hd approve`). The daemon owns merges, trains, deploys,
  leases and dispatch. Server devs do their own fork GitHub work (push, fork PRs, CI).
- Stage executors are spawned from .claude/agents/ with one change and one stage each (`hd dispatch <slug>`
  grants its leases and prints or starts the brief; the agent hands over with `hd handover`, never only in chat):
  `client-dev` + `client-qa` in this repo (dev's own check: built + the changed path tried on the device → master;
  client-qa runs only the release gate, full regression + Network QA → vX.Y.Z); `server-dev` + `server-qa` on the fork, each
  in its own clone `re/agents/<name>/or` (dev's own check: accuracy spec + green CI → integration and live server;
  server-qa runs only the upstream gate → upstream PR).
- Tools are shared skills any role calls (docs/workflow.md "Shared skills"); only a companion's run at a fixed
  commit counts for a gate. Hooks enforce the role limits and the gates.
- MCP servers (docs/workflow.md "MCP servers"): `osrs-mcp` (OpenRune's: OSRS Wiki, gamevals, decoded cache;
  read-only, the accuracy baseline's lookup), `osrs-devtools` (the Android plugin host, under the device lock),
  and `osrs-devtools-desktop` (the isolated desktop RuneLite, under the desktop lock).

## Shared rules
- **Builds** go through `tools/buildq.sh` (classes gate/dev/measure with a memory guard; gates via `qa.sh gate` in
  one 5 GB container, dev builds via `qa.sh dev`). Client host builds are host/build.sh + `ninja -C re/build` +
  repack (no docker).
- **Devices**: leases (`hd lease`, or `tools/device-lock.sh` with `LOCK_NAME=<instance>` from tools/instances.tsv),
  granted per change at dispatch; an agent takes the instances its change lists without asking, heartbeats while
  using them, frees them right after; idle holds are reclaimed. A deploy drains and reserves every instance.
- **Evidence** goes in `re/evidence/<slug>/` in the main checkout, never a session scratchpad.
- **Testing tools over screenshots**: client, desktop and server state is read through the MCP data tools
  (`osrs-devtools` for the Android app, `osrs-devtools-desktop` for desktop RuneLite, `osrs-mcp` for wiki, gamevals and
  cache; `server-probe` for logs and packets). A screenshot is evidence only for how something is drawn.
- **Accuracy**: server behaviour must match the official server; divergences are caveats or fixes.
- **Privacy**: no client ever contacts Jagex or RuneScape servers. Every APK goes through the telemetry patch
  (tools/repack.sh). Never put the user's email or personal identifiers in HTTP requests; use a generic User-Agent.
- **The user's own RuneLite** (C:\Users\Alex\.runelite) is never read or modified.
- **Tester account**: never ::empty it, keep its GE offer (lobster), drop only items you spawned.
- **Processes**: only stop processes you started.
- **Commits**: pathspec-only (`git commit -- <paths>`), authored as Alex Perrin
  <44126061+AlexPerrin@users.noreply.github.com>, message ending in the Claude co-author line; PR texts end with
  the Claude Code line.
- **Denied actions**: don't work around them; report them to the orchestrator.
