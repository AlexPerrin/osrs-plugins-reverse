<!-- Posted 2026-10-18 16:03 UTC, deleted by the user; do not repost. -->

Thanks, that's clear. Plan on our side:

- One PR per row, opened against `main` directly, a few at a time so review isn't flooded. The independent ones go first (cannon, genie random event, rune pouch, plank sack), then the quests, farming, hunter, fairy rings, jewellery, diaries and the Grand Exchange in that order.
- Before opening, we renumber our server-only dbtables, varps and timers against what's on `main`, and run `spotlessApply` (four of our branches fail `spotlessCheck` on formatting).
- Wiki-derived data stays as it is, with the source named in each PR.

On clue scrolls: our Treasure Trails work covers beginner to hard on the cache's `cluehelper_*` dbtables (clue texts, npc/loc/coord steps, emotes with Uri, STASH units, maps, challenge scrolls, puzzle boxes, the hard foes, caskets). We'll hold it back so it doesn't collide with yours. If any of it is useful to you, we can open it as a draft for you to take pieces from, or split out the generic bits it needs: `NpcOpOverrides` / `LocOpOverrides` (content claiming an npc/loc op ahead of its default handler for some players), playing tracks from the music tab, and opening the remaining plain drawers and chests. Which would you prefer?

🤖 Generated with [Claude Code](https://claude.com/claude-code)
