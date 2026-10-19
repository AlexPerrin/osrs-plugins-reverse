# osrs-plugins-reverse

RuneLite plugins running inside the official OSRS Android app (native/ hooks plus the host/ Java plugin host),
tested against an OpenRune test server (openrune/; upstream branches in re/upstream-pr/or, the fork
AlexPerrin/OpenRune-Server).

How work is organised (tracks, agents, gates): docs/workflow.md. Current state, read first:
- client: docs/client-state.md
- server: STATE.md on the fork's `docs` branch (`git -C re/upstream-pr/or show docs:STATE.md`)

History (docs/client-handoff.md, docs/server-handoff.md) is read only when needed.

## Roles
- Both tracks run one loop: trigger → analyse → build → verify → ship (docs/workflow.md).
- The orchestrator (the main session) routes tasks and blockers, and owns every ship step: pushes, PRs, merges,
  tags, deploys, device grants, progress files and decisions.
- One dev/QA pair per track, spawned from .claude/agents/ with one task each and its definition of done:
  `client-dev` + `client-qa` in this repo (merge and release gates); `server-dev` + `server-qa` on the fork, each
  in its own clone `re/agents/<name>/or` (internal gate → live server, then upstream gate → upstream PR).
- Tools are shared skills any role calls (docs/workflow.md "Shared skills"); only a companion's run at a fixed
  commit counts for a gate. Hooks enforce the role limits and the gates.
- MCP servers (docs/workflow.md "MCP servers"): `osrs-mcp` (OpenRune's: OSRS Wiki, gamevals, decoded cache;
  read-only, the accuracy baseline's lookup), `osrs-devtools` (the Android plugin host, under the device lock),
  and `osrs-devtools-desktop` (the isolated desktop RuneLite, under the desktop lock).

## Shared rules
- **Builds** go through `tools/buildq.sh` (classes gate/dev/measure with a memory guard; gates via `qa.sh gate` in
  one 5 GB container, dev builds via `qa.sh dev`). Client host builds are host/build.sh + `ninja -C re/build` +
  repack (no docker).
- **Devices**: `tools/device-lock.sh` (`LOCK_NAME=desktop` for desktop RuneLite), granted by the orchestrator.
  Server restarts reserve both devices.
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
