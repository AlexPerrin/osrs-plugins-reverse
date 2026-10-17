#pragma once
#include <cstdint>
#include <vector>

namespace draw_hide {
// Renderable draw listeners (RuneLite Hooks.registerRenderableDrawListener, Entity Hider): the
// actors (Npc*/Player*) in `actors` are not drawn (nor picked) by the scene renderer until the next
// call. Patches the vtables' slot 0 (getModel) of the actors' classes on first use; a hidden actor's
// getModel returns an empty model only when the renderer's drawRenderable asks for it. Game thread.
void set_hidden(const std::vector<uintptr_t>& actors);
}  // namespace draw_hide
