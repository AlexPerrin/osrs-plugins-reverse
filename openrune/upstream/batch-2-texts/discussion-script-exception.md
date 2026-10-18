# Discussion: should an exception in a content script log the player out?

**What happens now.** `PlayerInputProcess`, `PlayerMainProcess`, `PlayerPostTickProcess` and
`PlayerRouteRequestProcess` wrap each player's cycle in `tryOrDisconnect`: any `Exception` (or
`NotImplementedError`) calls `forceDisconnect()` and logs the error. Dialogue and interaction scripts run inside
that cycle, so a bug in one NPC's dialogue sends the player back to the login screen. Example: Talk-to on the
Lumbridge Doomsayer threw `IllegalStateException: Error Loading MesAnim` (a missing `mesanim.scared` entry; the
data fix is a separate patch) and the player was disconnected mid-conversation.

**Why it is like this (as we read it).** Disconnecting is the safe default: after an unknown failure the player's
state (open interfaces, queues, a half-run transaction) may be inconsistent, and a fresh login rebuilds it.

**Possible alternative.** Treat an exception raised inside a suspended content script (the player's active
coroutine: dialogue, `ProtectedAccess` work) differently from one raised in the engine's own per-player processing:
cancel that coroutine, close modals and the chatbox, clear the interaction, send a short game message, log the
error, and keep the session. Engine-level failures (input decoding, movement, info updates) would still disconnect.

**Open questions for maintainers.**
- Is keeping the session after a script failure acceptable, or is the disconnect intentional (for example, to make
  content bugs loud during development)?
- If acceptable: is the coroutine boundary (`GameCoroutine` / `ProtectedAccessLauncher`) the right place to catch,
  and should a repeated failure (same script, same player) still disconnect?
- Should item transactions inside a failed script be guaranteed atomic first (most already are, via
  `invTransaction`)?

We have not written a patch for this; we'd follow whatever direction you prefer.

🤖 Generated with [Claude Code](https://claude.com/claude-code)
