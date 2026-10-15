package net.runelite.client.callback;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import net.runelite.api.Renderable;

/**
 * Replaces RuneLite's Hooks (same name) with the part plugins call: renderable draw
 * listeners. They are recorded but not consulted yet (no draw hook into the game's renderer).
 */
public class Hooks {
    /** Same contract as RuneLite's: return false to hide the renderable this frame. */
    public interface RenderableDrawListener extends RenderCallback {
        boolean draw(Renderable renderable, boolean drawingUI);
    }

    private final List<RenderableDrawListener> listeners = new CopyOnWriteArrayList<>();

    public void registerRenderableDrawListener(RenderableDrawListener l) { listeners.add(l); }
    public void unregisterRenderableDrawListener(RenderableDrawListener l) { listeners.remove(l); }

    public boolean draw(Renderable renderable, boolean drawingUI) {
        for (RenderableDrawListener l : listeners) if (!l.draw(renderable, drawingUI)) return false;
        return true;
    }
}
