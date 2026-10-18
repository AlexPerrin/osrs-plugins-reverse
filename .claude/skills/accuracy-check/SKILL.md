---
name: accuracy-check
description: "Write or check a server feature's accuracy spec against OSRS sources (wiki, cache, Mod posts), and check behaviour against it. Use before coding a server feature, and in server QA."
---
# Accuracy check

The spec is `accuracy/<slug>.md` on the fork's `docs` branch (QA.md section 2). Look sources up with the
`osrs-mcp` MCP (`wiki_search`, `wiki_page`, `wiki_npc_spawns`, `cache_search`; load with ToolSearch).

## Writing the spec (server-dev)
1. What upstream already has: `gh pr list -R OpenRune/OpenRune-Server --search <topic>`, issues, recent `main`.
2. Each mechanic: its source (wiki page and section, cache entry, Mod post), the planned behaviour.
3. Caveats and guesses, each marked.
4. The tests, one per rule.
5. The PR split and `Depends:` (docs/workflow.md "Changes for upstream").

## Checking (server-qa)
1. Re-read each source yourself; don't rely on the spec's quotes.
2. Check the branch's behaviour (tests, in-game) against each rule.
3. Every gameplay-visible divergence is either fixed or listed in the PR's Caveats.

## Report
Per rule: matches, diverges (with the evidence), or a guess. Then the caveats the PR must list.
