# osrs-plugins-reverse

**This repo is the client and its APK releases only**: RuneLite plugins running inside the official OSRS Android app
(native/ hooks plus the host/ Java plugin host), our independent mobile RuneLite client, tested against an OpenRune test
server. Server code and tooling belong to the OpenRune fork (AlexPerrin/OpenRune-Server, clone `~/src/OpenRune-Server`;
tooling on its `local/server-tools` branch, `~/src/openrune-work/server-tools`); the client reads server values only
through `SERVER_TOOLS`. Everything about how work is run (the harness, hooks, agents, skills, MCP servers, leases, gates)
belongs to **AlexPerrin/osrs-harness** (`~/osrs-harness`): sessions and agents run from there, and its CLAUDE.md and
HANDOFF.md are the entry point. Nothing of that is kept in this repo.

State, read first: docs/client-state.md (client); `python3 ~/osrs-harness/hd.py status` (changes, batches, approvals);
STATE.md on the fork's `docs` branch (server). History (docs/client-handoff.md) is read only when needed.

## Rules for this repo
- **Builds**: host/build.sh, `ninja -C re/build`, then tools/repack.sh (7 `telemetry:` lines).
- **Releases**: dev builds via tools/release-dev.sh; official vX.Y.Z only after the release gate (qa/client-X.Y.Z.md)
  and the user's OK.
- **Privacy**: no client ever contacts Jagex or RuneScape servers. Every APK goes through the telemetry patch
  (tools/repack.sh). Never put the user's email or personal identifiers in HTTP requests; use a generic User-Agent.
- **Evidence** goes in `re/evidence/<slug>/`, never a session scratchpad.
- **Commits**: pathspec-only (`git commit -- <paths>`), authored as Alex Perrin
  <44126061+AlexPerrin@users.noreply.github.com>, message ending in the Claude co-author line; PR texts end with
  the Claude Code line.
