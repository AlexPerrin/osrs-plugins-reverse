package net.runelite.client.callback;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import net.runelite.api.Renderable;

/**
 * Replaces RuneLite's Hooks (same name) with the part plugins call: renderable draw
 * listeners. The host asks them about every NPC and player once per cycle (drawingUI false) and
 * the ones they reject are hidden from the game's scene renderer (net.runelite.host.Host,
 * native draw_hide). Projectiles, graphics objects and 2D elements (drawingUI true) are not asked.
 */
public class Hooks {
    /** Same contract as RuneLite's: return false to hide the renderable this frame. */
    public interface RenderableDrawListener extends RenderCallback {
        boolean draw(Renderable renderable, boolean drawingUI);
    }

    private final List<RenderableDrawListener> listeners = new CopyOnWriteArrayList<>();

    public void registerRenderableDrawListener(RenderableDrawListener l) { listeners.add(l); }
    public void unregisterRenderableDrawListener(RenderableDrawListener l) { listeners.remove(l); }

    public boolean hasListeners() { return !listeners.isEmpty(); }

    public boolean draw(Renderable renderable, boolean drawingUI) {
        for (RenderableDrawListener l : listeners) if (!l.draw(renderable, drawingUI)) return false;
        return true;
    }
}
