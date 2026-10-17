package net.runelite.host;

import android.util.Log;
import android.view.MotionEvent;
import java.awt.event.InputEvent;
import java.awt.event.MouseEvent;
import java.util.List;
import net.runelite.client.input.MouseManager;

/**
 * Touches as mouse events for plugin MouseListeners (RuneLite's MouseManager, e.g. Screen
 * Markers' drag to draw a marker). Called by the touch router on the UI thread, as desktop calls
 * MouseManager from the AWT thread. A gesture belongs to the plugins when the press is consumed:
 * then its moves and release are swallowed too and the game sees none of it. Otherwise the whole
 * gesture goes to the game unchanged (later consumes can't take back what the game already got).
 * With no listener registered nothing is built and every touch passes through.
 * Coordinates are on the UI canvas, as getMouseCanvasPosition.
 */
final class TouchMouse {
    private TouchMouse() {}

    static volatile MouseManager manager;
    private static java.lang.reflect.Field listenersField;
    private static final java.awt.Component SOURCE = new java.awt.Canvas();
    private static final int TAP_SLOP = 6;  // canvas units a press may move and still click

    private static boolean owned;   // the current gesture's press was consumed
    private static boolean active;  // a press was sent to the listeners
    private static int downX, downY;
    private static boolean moved;

    private static boolean hasListeners() {
        MouseManager m = manager;
        if (m == null) return false;
        try {
            if (listenersField == null) {
                listenersField = MouseManager.class.getDeclaredField("mouseListeners");
                listenersField.setAccessible(true);
            }
            return !((List<?>) listenersField.get(m)).isEmpty();
        } catch (Throwable t) {
            return false;
        }
    }

    private static int[] canvas(float x, float y) {
        int[] screen = Native.screenSize();
        java.awt.Dimension c = OverlayRenderer.canvasSize();
        if (screen == null || screen[0] <= 0 || screen[1] <= 0 || c.width <= 0) return null;
        return new int[] {Math.round(x * c.width / screen[0]), Math.round(y * c.height / screen[1])};
    }

    /** True when the plugins took this touch (the router then doesn't pass it to the game). */
    static boolean onTouch(MotionEvent e) {
        int action = e.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN) {
            owned = false;
            active = false;
            if (!hasListeners()) return false;
        } else if (!active) {
            return false;
        }
        int[] p = canvas(e.getX(), e.getY());
        if (p == null) return owned;
        MouseManager m = manager;
        long now = System.currentTimeMillis();
        int down = InputEvent.BUTTON1_DOWN_MASK | InputEvent.BUTTON1_MASK;
        try {
            switch (action) {
                case MotionEvent.ACTION_DOWN: {
                    active = true;
                    moved = false;
                    downX = p[0];
                    downY = p[1];
                    MouseEvent ev = m.processMousePressed(new MouseEvent(SOURCE, MouseEvent.MOUSE_PRESSED, now, down, p[0], p[1], 1, false, MouseEvent.BUTTON1));
                    owned = ev == null || ev.isConsumed();
                    if (owned) Log.i(Host.TAG, "touch taken by a plugin mouse listener at " + p[0] + "," + p[1]);
                    return owned;
                }
                case MotionEvent.ACTION_MOVE:
                    if (Math.abs(p[0] - downX) > TAP_SLOP || Math.abs(p[1] - downY) > TAP_SLOP) moved = true;
                    m.processMouseDragged(new MouseEvent(SOURCE, MouseEvent.MOUSE_DRAGGED, now, down, p[0], p[1], 0, false, MouseEvent.NOBUTTON));
                    return owned;
                case MotionEvent.ACTION_UP:
                    active = false;
                    m.processMouseReleased(new MouseEvent(SOURCE, MouseEvent.MOUSE_RELEASED, now, InputEvent.BUTTON1_MASK, p[0], p[1], 1, false, MouseEvent.BUTTON1));
                    if (!moved) m.processMouseClicked(new MouseEvent(SOURCE, MouseEvent.MOUSE_CLICKED, now, InputEvent.BUTTON1_MASK, p[0], p[1], 1, false, MouseEvent.BUTTON1));
                    return owned;
                case MotionEvent.ACTION_CANCEL:
                    active = false;
                    return owned;
                default:
                    return owned;  // second fingers etc. follow the gesture's owner
            }
        } catch (Throwable t) {
            Log.e(Host.TAG, "plugin mouse listener failed", t);
            return owned;
        }
    }
}
