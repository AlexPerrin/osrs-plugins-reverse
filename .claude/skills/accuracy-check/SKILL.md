---
name: accuracy-check
description: "Write or check a server feature's accuracy spec against OSRS sources (wiki, cache, Mod posts), and check behaviour against it. Use before coding a server feature, and in server QA."
---
# Accuracy check

The spec is `accuracy/<slug>.md` on the fork's `docs` branch (QA.md section 2). Look sources up with the
`osrs-mcp` MCP (`wiki_search`, `wiki_page`, `wiki_npc_spawns`, `cache_search`; load with ToolSearch).

## Sources, in order
1. **OSRS Wiki** (pages, transcripts, update history), the **cache** (osrs-mcp `cache_search`, gamevals) and **Mod
   posts**: the accuracy baseline. A rule cites one of these, or it's a guess.
2. **Reference implementations**, read-only clones in `re/ref/` (refresh with `git -C re/ref/<repo> pull`), for logic
   the wiki doesn't spell out: tick timings, dialogue flow and branches, state machines, edge cases (interrupted
   dialogue, full inventory, logout mid-event). They are evidence for a guess, never a source for a rule:
   - `re/ref/Content/scripts` (LostCity, MIT): RuneScript content of 2004 RuneScape (quests incl. mcannon, doric,
     gobdip, hetty, cook, the macro/random events, skills, shops). Jagex's script style, so timings and flows are
     often close to the original, but it's **pre-OSRS**: OSRS has changed much since (reworks, new dialogue,
     QoL). Check the OSRS Wiki's history for the mechanic before trusting it. Engine: `re/ref/Engine-TS` (MIT).
   - `re/ref/project-gielinor-rust-server` (ISC, OSRS 239.1, started 2026-10): young and unverified, and its own
     SOURCE_PROVENANCE.md leaves open questions about derived code. Its Wiki-research issues can point to sources;
     its code is a last resort.
   Read for behaviour, never copy code or text into our branches (different languages anyway; licences and
   provenance differ). In the spec, say "per LostCity <file>" next to a guess so QA can check it; the PR's Data
   sources may name LostCity as a reference only when a rule followed it.
3. The RuneLite plugins that read the mechanic (what they parse is what the client expects).

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
