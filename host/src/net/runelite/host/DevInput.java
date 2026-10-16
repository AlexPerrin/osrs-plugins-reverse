package net.runelite.host;

import android.app.Instrumentation;
import android.os.SystemClock;
import android.util.Log;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;
import java.awt.AWTEvent;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Desktop input for plugins that drive the client (OpenRune-Developer-Tools): Client.getCanvas()
 * returns {@link #CANVAS}, whose dispatchEvent turns AWT mouse and key events into Android input.
 * <ul>
 * <li>Button 1 press/release: a tap (touch down/up) at the point, in canvas units scaled to the screen.
 * <li>Button 3: a long-press (down, up ~1.1 s later), which opens the game's menu.
 * <li>Mouse moves: a virtual mouse point (no touch). While it is fresh, getMouseCanvasPosition()
 *     returns it and getMenuEntries() with no menu open is the menu the game builds there.
 * <li>Keys: Android key events (typed characters through the key character map).
 * </ul>
 * Events go to the game window through Instrumentation on one background thread, in order, so the
 * caller (often the game thread, via SwingUtilities.invokeAndWait) never blocks on the UI thread.
 */
final class DevInput {
    private DevInput() {}

    private static final String TAG = Host.TAG;
    static final long LONG_PRESS_MS = 1100, VIRTUAL_MOUSE_MS = 3000;
    private static final ExecutorService INPUT = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "devinput");
        t.setDaemon(true);
        return t;
    });
    private static final Instrumentation INSTRUMENTATION = new Instrumentation();

    private static volatile int mouseX = -1, mouseY = -1;
    private static volatile long mouseMillis;
    private static volatile long downTime;
    private static volatile boolean longPress;

    static final java.awt.Canvas CANVAS = new java.awt.Canvas() {
        @Override public void dispatchEvent(AWTEvent e) { DevInput.dispatch(e); }
        @Override public int getWidth() { return OverlayRenderer.canvasSize().width; }
        @Override public int getHeight() { return OverlayRenderer.canvasSize().height; }
        @Override public java.awt.Dimension getSize() { return OverlayRenderer.canvasSize(); }
        @Override public boolean isShowing() { return true; }
        @Override public boolean hasFocus() { return true; }
    };

    /** The virtual mouse in canvas units while fresh, else null. */
    static java.awt.Point virtualMouse() {
        if (mouseX < 0 || SystemClock.uptimeMillis() - mouseMillis > VIRTUAL_MOUSE_MS) return null;
        return new java.awt.Point(mouseX, mouseY);
    }

    static void setVirtualMouse(int x, int y) {
        mouseX = x;
        mouseY = y;
        mouseMillis = SystemClock.uptimeMillis();
    }

    private static float[] toScreen(int x, int y) {
        int[] screen = Native.screenSize();
        java.awt.Dimension c = OverlayRenderer.canvasSize();
        if (screen == null || c.width <= 0 || c.height <= 0) return null;
        return new float[] {x * (float) screen[0] / c.width, y * (float) screen[1] / c.height};
    }

    /**
     * Synthesized touches and keys are real input to the whole app: only while in game, never on the
     * login, sign-in or account screens (sign-in safety rule). Mouse moves (no input) are always fine.
     */
    static boolean inputAllowed() {
        net.runelite.api.Client c = Host.client();
        boolean ok = c != null && c.getGameState() == net.runelite.api.GameState.LOGGED_IN;
        if (!ok) Log.w(TAG, "devinput: refused (game state " + (c != null ? c.getGameState() : null) + ", not LOGGED_IN)");
        return ok;
    }

    static void dispatch(AWTEvent e) {
        if (e instanceof java.awt.event.MouseEvent) {
            java.awt.event.MouseEvent m = (java.awt.event.MouseEvent) e;
            boolean move = m.getID() == java.awt.event.MouseEvent.MOUSE_MOVED || m.getID() == java.awt.event.MouseEvent.MOUSE_ENTERED
                    || m.getID() == java.awt.event.MouseEvent.MOUSE_EXITED || m.getID() == java.awt.event.MouseEvent.MOUSE_CLICKED;
            if (move || inputAllowed()) mouse(m);
        } else if (e instanceof java.awt.event.KeyEvent) {
            if (inputAllowed()) key((java.awt.event.KeyEvent) e);
        }
    }

    private static void mouse(java.awt.event.MouseEvent e) {
        int x = e.getX(), y = e.getY();
        setVirtualMouse(x, y);
        final float[] p = toScreen(x, y);
        if (p == null) return;
        switch (e.getID()) {
            case java.awt.event.MouseEvent.MOUSE_PRESSED: {
                longPress = e.getButton() == java.awt.event.MouseEvent.BUTTON3;
                INPUT.execute(() -> {
                    downTime = SystemClock.uptimeMillis();
                    touch(MotionEvent.ACTION_DOWN, downTime, p);
                });
                break;
            }
            case java.awt.event.MouseEvent.MOUSE_DRAGGED:
                INPUT.execute(() -> touch(MotionEvent.ACTION_MOVE, downTime, p));
                break;
            case java.awt.event.MouseEvent.MOUSE_RELEASED: {
                final boolean hold = longPress;
                INPUT.execute(() -> {
                    if (hold) {
                        long wait = downTime + LONG_PRESS_MS - SystemClock.uptimeMillis();
                        if (wait > 0) SystemClock.sleep(wait);
                    }
                    touch(MotionEvent.ACTION_UP, downTime, p);
                });
                break;
            }
            default:  // moved/clicked/entered: the virtual mouse only
                break;
        }
    }

    private static void touch(int action, long down, float[] p) {
        MotionEvent ev = MotionEvent.obtain(down, SystemClock.uptimeMillis(), action, p[0], p[1], 0);
        ev.setSource(InputDevice.SOURCE_TOUCHSCREEN);
        try {
            INSTRUMENTATION.sendPointerSync(ev);
        } catch (Throwable t) {
            Log.w(TAG, "devinput: touch failed: " + t);
        } finally {
            ev.recycle();
        }
    }

    /** A tap at canvas (x, y), asynchronous. */
    static void tap(int x, int y) {
        if (!inputAllowed()) return;
        final float[] p = toScreen(x, y);
        if (p == null) return;
        INPUT.execute(() -> {
            long d = SystemClock.uptimeMillis();
            touch(MotionEvent.ACTION_DOWN, d, p);
            SystemClock.sleep(40);
            touch(MotionEvent.ACTION_UP, d, p);
        });
    }

    private static void key(java.awt.event.KeyEvent e) {
        int id = e.getID();
        char c = e.getKeyChar();
        if (id == java.awt.event.KeyEvent.KEY_TYPED) {
            if (c >= 0x20 && c != 0x7f && c != java.awt.event.KeyEvent.CHAR_UNDEFINED) {
                final String s = String.valueOf(c);
                INPUT.execute(() -> {
                    try {
                        INSTRUMENTATION.sendStringSync(s);
                    } catch (Throwable t) {
                        Log.w(TAG, "devinput: text failed: " + t);
                    }
                });
            }
            return;
        }
        int code = androidKey(e.getKeyCode());
        if (code == KeyEvent.KEYCODE_UNKNOWN) return;  // printable keys arrive as KEY_TYPED
        final int action = id == java.awt.event.KeyEvent.KEY_PRESSED ? KeyEvent.ACTION_DOWN : KeyEvent.ACTION_UP;
        INPUT.execute(() -> {
            try {
                INSTRUMENTATION.sendKeySync(new KeyEvent(action, code));
            } catch (Throwable t) {
                Log.w(TAG, "devinput: key failed: " + t);
            }
        });
    }

    private static int androidKey(int vk) {
        switch (vk) {
            case java.awt.event.KeyEvent.VK_ENTER: return KeyEvent.KEYCODE_ENTER;
            case java.awt.event.KeyEvent.VK_ESCAPE: return KeyEvent.KEYCODE_ESCAPE;
            case java.awt.event.KeyEvent.VK_BACK_SPACE: return KeyEvent.KEYCODE_DEL;
            case java.awt.event.KeyEvent.VK_TAB: return KeyEvent.KEYCODE_TAB;
            case java.awt.event.KeyEvent.VK_UP: return KeyEvent.KEYCODE_DPAD_UP;
            case java.awt.event.KeyEvent.VK_DOWN: return KeyEvent.KEYCODE_DPAD_DOWN;
            case java.awt.event.KeyEvent.VK_LEFT: return KeyEvent.KEYCODE_DPAD_LEFT;
            case java.awt.event.KeyEvent.VK_RIGHT: return KeyEvent.KEYCODE_DPAD_RIGHT;
            default:
                if (vk >= java.awt.event.KeyEvent.VK_F1 && vk <= java.awt.event.KeyEvent.VK_F12)
                    return KeyEvent.KEYCODE_F1 + (vk - java.awt.event.KeyEvent.VK_F1);
                return KeyEvent.KEYCODE_UNKNOWN;
        }
    }
}
