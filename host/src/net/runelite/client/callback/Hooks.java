package net.runelite.client.callback;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import net.runelite.api.Renderable;
import net.runelite.client.eventbus.EventBus;

/**
 * Replaces RuneLite's Hooks (same name), the client's Callbacks (Client.getCallbacks): post and
 * postDeferred go to the host's EventBus (deferred: at the end of the tick, as desktop), and the
 * renderable draw listeners plugins register. The host asks them about every NPC and player once per
 * cycle (drawingUI false) and the ones they reject are hidden from the game's scene renderer
 * (net.runelite.host.Host, native draw_hide). Projectiles, graphics objects and 2D elements (drawingUI
 * true) are not asked. The game-side callbacks (tick, frame, draw, input) are the host's own cycle and
 * input paths, so here they do nothing and input events pass through unchanged.
 */
public class Hooks implements net.runelite.api.hooks.Callbacks {
    /** Same contract as RuneLite's: return false to hide the renderable this frame. */
    public interface RenderableDrawListener extends RenderCallback {
        boolean draw(Renderable renderable, boolean drawingUI);
    }

    private final List<RenderableDrawListener> listeners = new CopyOnWriteArrayList<>();
    private volatile EventBus eventBus;
    private volatile ClientThread clientThread;

    /** Host: the EventBus and ClientThread events go to, once the host is up. */
    public void attach(EventBus bus, ClientThread thread) {
        eventBus = bus;
        clientThread = thread;
    }

    public void registerRenderableDrawListener(RenderableDrawListener l) { listeners.add(l); }
    public void unregisterRenderableDrawListener(RenderableDrawListener l) { listeners.remove(l); }

    public boolean hasListeners() { return !listeners.isEmpty(); }

    @Override
    public boolean draw(Renderable renderable, boolean drawingUI) {
        for (RenderableDrawListener l : listeners) if (!l.draw(renderable, drawingUI)) return false;
        return true;
    }

    @Override public void post(Object event) {
        EventBus b = eventBus;
        if (b != null) b.post(event);
    }

    @Override public void postDeferred(Object event) {
        ClientThread t = clientThread;
        if (t != null) t.invokeAtTickEnd(() -> post(event));
    }

    @Override public void tick() { }
    @Override public void tickEnd() { }
    @Override public void frame() { }
    @Override public void serverTick() { }
    @Override public void drawScene() { }
    @Override public void drawAboveOverheads() { }
    @Override public void draw(net.runelite.api.MainBufferProvider p, java.awt.Graphics g, int x, int y) { }
    @Override public void drawInterface(int interfaceId, List<net.runelite.api.widgets.WidgetItem> items) { }
    @Override public void drawLayer(net.runelite.api.widgets.Widget layer, List<net.runelite.api.widgets.WidgetItem> items) { }
    @Override public java.awt.event.MouseEvent mousePressed(java.awt.event.MouseEvent e) { return e; }
    @Override public java.awt.event.MouseEvent mouseReleased(java.awt.event.MouseEvent e) { return e; }
    @Override public java.awt.event.MouseEvent mouseClicked(java.awt.event.MouseEvent e) { return e; }
    @Override public java.awt.event.MouseEvent mouseEntered(java.awt.event.MouseEvent e) { return e; }
    @Override public java.awt.event.MouseEvent mouseExited(java.awt.event.MouseEvent e) { return e; }
    @Override public java.awt.event.MouseEvent mouseDragged(java.awt.event.MouseEvent e) { return e; }
    @Override public java.awt.event.MouseEvent mouseMoved(java.awt.event.MouseEvent e) { return e; }
    @Override public java.awt.event.MouseWheelEvent mouseWheelMoved(java.awt.event.MouseWheelEvent e) { return e; }
    @Override public void keyPressed(java.awt.event.KeyEvent e) { }
    @Override public void keyReleased(java.awt.event.KeyEvent e) { }
    @Override public void keyTyped(java.awt.event.KeyEvent e) { }
    @Override public void error(String message, Throwable reason) { android.util.Log.e("osrsrl-host", message, reason); }
    @Override public void openUrl(String url) { net.runelite.client.util.LinkBrowser.browse(url); }
    @Override public boolean isRuneLiteClientOutdated() { return false; }
}
