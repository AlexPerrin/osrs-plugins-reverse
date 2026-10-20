# Orchestrator handoff

Rewritten at each milestone so a fresh orchestrator session can take over without the old conversation. Read with
CLAUDE.md, docs/client-state.md, the fork's STATE.md (`git -C re/upstream-pr/or show fork/docs:STATE.md`) and
docs/backlog.md (upstream queue, roadmap, client and server tasks). Last rewrite: 2026-10-19 ~21:00 (handoff for restart).

## How the orchestrator works
- Delegate: anything beyond a couple of quick commands goes to a subagent (typed roles from .claude/agents/, or a
  one-off general agent for tooling/research). Relay verdicts and decisions; don't pull logs into the main context.
- Restart only to avoid repeated compaction (not on a schedule): rewrite this file first, then a fresh session takes over. Subagents don't survive a session;
  their work is on disk (clones under re/agents/<name>/, docs worktrees, records on the fork's docs branch), so a
  new orchestrator re-dispatches from the "In flight" list below.
- Gates (2026-10-19): server and client devs ship on their own check (server: accuracy spec incl. RSProx + green
  fork CI → integration/live; client: built + changed path tried on the device → master). QA runs only the server
  upstream gate and the client release gate. Older notes below that say "internal PASS" mean the dev's check.
- Upstream: nothing reaches OpenRune/* without the user's OK; print the exact action first; append comments, never
  edit an original PR description; merge main into PR branches rather than force-push.

## Live
- Server: integration 5cc473397 (image osrs-openrune:integration-5cc4733), rollback osrs-openrune:rollback-5da8d3e.
- Client: v0.1.0 released; dev builds via tools/release-dev.sh.
- Build system: one-container QA gate + shared build cache + dev lane (qa.sh gate / dev), buildq classes.

## Queue for the next orchestrator (nothing in flight at handoff, 2026-10-19 ~20:30)
All agents finished. Dispatch in roughly this order (server QA can run two companions in parallel; ask the user
before adding more agents than that). Records: qa/<slug>.md on the fork's docs; branches on the fork unless noted.

Build evidence: server gates now take build/test from the fork's GitHub Actions CI at the exact commit
(.claude/skills/build-test/SKILL.md "CI first"); a first run on content/sheep-shearer 069c15fde was started
2026-10-19 (check its result: `gh run list -R AlexPerrin/OpenRune-Server --branch content/sheep-shearer`).

Server QA (companions sq24 for sd23's branches, sq25 for sd24's):
1. Sheep Shearer RSProx comparison: content/sheep-shearer 069c15fde (both gates PASS). Recordings 1718 (rev 227,
   Leagues V, whole quest, ticks 7184-7744) and 1619-1622 (rev 225, shearing only); transcripts
   re/ref/rsprox/txt/, notes re/ref/rsprox/notes/sheep-shearer.md. Then the #366 update (user OK): fast-forward
   push 069c15fde onto content/quests-sheep-romeo, the review comment (stile fixed separately), ready vs draft.
2. acc/genie e9c7bcac3 (fork #18): full re-gate; zones moved into dbtable.random_event_zones. Search RSProx (NPC).
3. fix/goblin-village-lookout-ladder 475eefb76: both gates, in game.
4. fix/57 merge e551ba7ce (#362, NOT on the fork yet; in re/agents/sd24/or, worktree w57, clone g57): both gates,
   in game; then the user's OK for the fast-forward push, the drafted comment (pr-texts/gameframe-fullscreen-map.comment)
   and ready.
5. Goblin Diplomacy 4f5e59627 and Witch's Potion 4dc1ac9c7 (-829 branches): internal PASS; upstream needs the
   RSProx comparison (search by NPC id / TEXT; nothing transcribed yet) and, for Goblin Diplomacy, the text fix
   (gate + in-game run + cheats; "moved verbatim from #328"). Then the user's OK to replace #367/#368 (merge commit).
6. acc/cannon bc243d141: dev run first (sd23's report: wt/acc-cannon to bc243d141, delete .data/cache/SERVER,
   qa.sh dev; fill RESULTS_PENDING), then the gates.
7. fix/stile-approach 8a8c0ca7a: internal FAIL (the player walks onto the stile's own blocking tile, so it never
   climbs). Fix: start from the tile outside the stile on the player's side, in line; check in game; text update.

Client:
8. Done: gen 46's five branches (scene-item-despawn 359aa21, rebuild-double-logged-in f01f851, host-config-rsprofiles
   77baa50, host-javax-sound-shim e8a3836, poison-sprite-override e2ee7b7) merged to master on the dev's check (new client
   model, no merge gate); master builds (host/build.sh + ninja). Pushed. Device restore checks: restore
   settings.properties first (the login now writes rsprofile keys).

Other: the re-pin (docs/backlog.md `repin-2`: drop fix/17, fix/18, fin/doric; add the gated branches); the
deny_publish `git stash push` false positive; RSProx coverage for the roadmap items.

## Waiting on the user
- #366 Sheep Shearer (both gates PASS at 069c15fde): on hold until the RSProx comparison; then the fast-forward push,
  the review comment (printed in the conversation; stile fixed separately), and ready vs draft.
- #370 (rune pouch) and #371 (Edgeville store) are open upstream; #351 leaves draft after #371 merges.
- #354/#355/#367/#368: gated rewrites replace their content with a non-force-push merge commit; each needs the
  user's OK when it arrives.
- RSProx: the rsprox item in the PR template: mark N/A on non-content PRs? (asked, not answered).
- Docker clean-up (~16 GB): the command is for the user to run (the classifier blocks it for agents).

## Locks and resources
Device: free (runs gen 46's poison-sprite-override build; the tester has a fish barrel in slot 15 until the server's barrel Destroy fix). Desktop: free, RuneLite on the live target. BlueStacks Pie64 must be started on
Windows after a reboot/WSL restart (`HD-Player.exe --instance Pie64`). WSL has 20 GB (C:\Users\Alex\.wslconfig).
