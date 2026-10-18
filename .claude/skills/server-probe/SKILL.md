---
name: server-probe
description: "Find out what the OpenRune test server does or sends: logs, packets, account data, gamevals and cache data. Use when a client feature depends on server behaviour, or to write a blocker's repro."
---
# Server probe (read-only)

- **Logs:** `docker logs osrs-openrune` (game), `osrs-openrune-http` (Android sidecar), `osrs-openrune-http-desktop`.
- **Packets:** the desktop RSProx transcript (`C:\osrs-test\home\.rsprox\binary`, see docs/multi-client.md);
  Android protocol: docs/android-protocol.md.
- **Account data:** `tools/openrune-sql.sh` for reads only; writes go through the orchestrator.
- **Gamevals and cache:** the `osrs-mcp` MCP (`gameval_search`, `cache_search`; load with ToolSearch). It reads
  our tree (`openrune/src`), not upstream `main`.
- **Game data:** `osrs-mcp` `wiki_search`, `wiki_page`, `wiki_npc_spawns`.

Never restart or edit the server, and only stop processes you started.

## Report
What the server did, with the log lines, packets or rows. For a blocker: the repro steps on the test server,
the expected behaviour and its source.
