# NPC and loc op overrides (content/op-overrides, patch 20a, unpushed)

Audited 2026-10-18 against the patch and the surrounding `LocInteractions` / `NpcInteractions` code on the branch
(no OSRS mechanic: engine API; wiki pages: none applicable). Report only.

## Summary
An engine hook with no player-visible behaviour of its own. With no claims registered, op resolution is unchanged
(one HashMap lookup returning null, then the existing chain). 0 divergences. One engine caveat (not an OSRS
divergence): the override is only consulted on the op path, not the ap path, so a claimed op on a type whose
normal handler is an Ap handler would be bypassed when the player is in ap range.

## Mechanics
| # | Mechanic | Official behaviour (source) | Patch behaviour (file:line or function) | Verdict |
|---|---|---|---|---|
| 1 | Default npc op resolution | n/a (engine) | `NpcInteractions.opTrigger`: claims check returns false with no registrations, then multinpc -> type -> content -> category -> unimplemented -> default, as before | matches (unchanged) |
| 2 | Default loc op resolution | n/a | `LocInteractions.opTrigger`: same, guarded by `base === loc` so the multiloc recursion is not re-checked | matches (unchanged) |
| 3 | Claimed op without a handler | n/a | Falls through to the normal chain (`eventBus.contains` check) | matches (safe) |
| 4 | Ap (approach) triggers | n/a | `apTrigger` unchanged; overrides not consulted | n/a (caveat below) |
| 5 | Constructor compatibility | n/a | Default `LocOpOverrides()` / `NpcOpOverrides()` args; injected singletons in the server | matches |
| 6 | Op range / walking | n/a | `interact` unchanged | matches (unchanged) |

## Divergences
None.

## Unsourced
None (no game data, strings, timings or ids).

## Not implemented (scope gaps)
- No Ap override (`LocOverrideEvents.Ap` / `NpcOverrideEvents.Ap`): if the overridden npc/loc has an `onApNpcN` /
  `onApLocN` handler, `hasApTrigger` is true and the engine may run that Ap handler before the claimed Op in ap
  range. Not declared; harmless for the first user (Doric's anvils use an op handler) but worth a doc comment or an
  ap-side check (`apTrigger` returning null for claimed ops) before other content relies on it.
- No dedicated unit test (declared; covered by `DoricsQuestInteractionTest`).
