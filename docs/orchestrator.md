# Orchestrator handoff

Rewritten at each milestone so a fresh orchestrator session can take over without the old conversation. Read with
CLAUDE.md, docs/client-state.md, the fork's STATE.md (`git -C re/upstream-pr/or show fork/docs:STATE.md`) and
docs/backlog.md (upstream queue, roadmap, client and server tasks). Last rewrite: 2026-10-19 evening.

## How the orchestrator works
- Delegate: anything beyond a couple of quick commands goes to a subagent (typed roles from .claude/agents/, or a
  one-off general agent for tooling/research). Relay verdicts and decisions; don't pull logs into the main context.
- Restart per milestone: rewrite this file, then a fresh session takes over. Subagents don't survive a session;
  their work is on disk (clones under re/agents/<name>/, docs worktrees, records on the fork's docs branch), so a
  new orchestrator re-dispatches from the "In flight" list below.
- Upstream: nothing reaches OpenRune/* without the user's OK; print the exact action first; append comments, never
  edit an original PR description; merge main into PR branches rather than force-push.

## Live
- Server: integration 5cc473397 (image osrs-openrune:integration-5cc4733), rollback osrs-openrune:rollback-5da8d3e.
- Client: v0.1.0 released; dev builds via tools/release-dev.sh.
- Build system: one-container QA gate + shared build cache + dev lane (qa.sh gate / dev), buildq classes.

## In flight (2026-10-19 evening)
| Owner | Work | State | Next |
|---|---|---|---|
| sd23 (server-dev) | acc/genie: move RandomEventLocations lists to a server-only dbtable (pattern: dbtable.music_classic); acc/cannon bc243d141: Ranged XP on capped damage, load message, caveats, template text | coding / dev builds | hand over → sq24 |
| sd24 (server-dev) | Witch's Potion text fixes (in-game run + cheats, id note); fix/goblin-village-lookout-ladder 475eefb76 dev run; fix/57 merge-main e551ba7ce dev run (#362) | in progress | hand over → sq25 |
| sq24 (server-qa) | fix/stile-approach 8a8c0ca7a in-game check (gate passed); then Sheep Shearer vs RSProx recordings | needs desktop lock (granted) | record qa/stile-approach.md, then the RSProx comparison |
| sq25 (server-qa) | content/goblin-diplomacy-829 4f5e59627 gates (incl. whether deleting #328's GoblinGenerals/Grubfoot is in scope) | gate + in-game | record qa/goblin-diplomacy.md |
| RSProx transcription (general) | gunzip + transcribe 1619-1622, 1718 (Sheep Shearer), then Goblin Diplomacy and Witch's Potion recordings; notes in re/ref/rsprox/notes/ | running | update references/rsprox.md on the fork's docs |
| client dev gen 46 | 5 host fixes, one branch each off master a063f11: scene-item-despawn 359aa21, rebuild-double-logged-in f01f851, host-config-rsprofiles 77baa50, host-javax-sound-shim e8a3836, poison-sprite-override (in progress) | holds the device | then one client-qa session gates all five |

## Waiting on the user
- #366 Sheep Shearer (both gates PASS at 069c15fde): on hold until the RSProx comparison; then the fast-forward push,
  the review comment (printed in the conversation; stile fixed separately), and ready vs draft.
- #370 (rune pouch) and #371 (Edgeville store) are open upstream; #351 leaves draft after #371 merges.
- #354/#355/#367/#368: gated rewrites replace their content with a non-force-push merge commit; each needs the
  user's OK when it arrives.
- RSProx: the rsprox item in the PR template: mark N/A on non-content PRs? (asked, not answered).
- Docker clean-up (~16 GB): the command is for the user to run (the classifier blocks it for agents).

## Locks and resources
Device: client dev gen 46. Desktop: grants to sq25 and server-qa (sq24). BlueStacks Pie64 must be started on
Windows after a reboot/WSL restart (`HD-Player.exe --instance Pie64`). WSL has 20 GB (C:\Users\Alex\.wslconfig).
