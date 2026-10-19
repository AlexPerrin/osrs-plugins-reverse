package net.runelite.host;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Window;
import java.util.List;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.input.KeyManager;

/**
 * Hardware keys for RuneLite KeyListeners (Key Remapping, hotkey plugins). Desktop runs the
 * listeners on each AWT key event before the game sees it; a listener may consume it or rewrite its
 * key code (Key Remapping's WASD to arrow keys). Here the game's Activity gets Android key events
 * through its Window.Callback (Sidebar's proxy): while any KeyListener is registered, each key event
 * is held, run through KeyManager on the client thread (listeners read game state), and then handed
 * to the game on the main thread unchanged, remapped, or not at all. Events keep their order: all
 * of them take the same path while listeners exist. System keys (back, volume, ...) and
 * ACTION_MULTIPLE (text injected as characters) go straight to the game.
 */
final class KeyRouter {
    private KeyRouter() {}

    static volatile KeyManager manager;
    static volatile ClientThread clientThread;
    private static java.lang.reflect.Field listenersField;
    private static final java.awt.Component SOURCE = new java.awt.Canvas();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private static boolean hasListeners() {
        KeyManager m = manager;
        if (m == null || clientThread == null) return false;
        try {
            if (listenersField == null) {
                listenersField = KeyManager.class.getDeclaredField("keyListeners");
                listenersField.setAccessible(true);
            }
            return !((List<?>) listenersField.get(m)).isEmpty();
        } catch (Throwable t) {
            return false;
        }
    }

    static boolean listenersActive() {
        return hasListeners();
    }

    /**
     * Main thread, from PromptEnter: the soft keyboard's Done (the game's chatbox/prompt submit) as a
     * VK_ENTER press, typed '\n' and release for the listeners on the client thread, queued behind any
     * held key events; then {@code submit} (the game's editor action) on the main thread, unless a
     * listener consumed the press.
     */
    static void editorEnter(final Runnable submit) {
        clientThread.invokeLater(() -> {
            boolean consumed = false;
            try {
                consumed = sendEnter();
            } catch (Throwable t) {
                Log.e(Host.TAG, "enter key listeners threw", t);
            }
            if ("1".equals(Native.property("debug.osrsrl.keylog"))) Log.i(Host.TAG, "key 10 (editor action)" + (consumed ? " consumed" : " submitted"));
            if (consumed) return;
            MAIN.post(() -> {
                try {
                    submit.run();
                } catch (Throwable t) {
                    Log.e(Host.TAG, "editor action failed", t);
                }
            });
        });
    }

    /** Client thread: true when a listener consumed the press (or its typed '\n'). */
    private static boolean sendEnter() {
        KeyManager m = manager;
        int vk = java.awt.event.KeyEvent.VK_ENTER;
        long now = System.currentTimeMillis();
        java.awt.event.KeyEvent press = new java.awt.event.KeyEvent(SOURCE, java.awt.event.KeyEvent.KEY_PRESSED, now, 0, vk, '\n');
        m.processKeyPressed(press);
        boolean consumed = press.isConsumed();
        if (!consumed) {
            java.awt.event.KeyEvent typed = new java.awt.event.KeyEvent(SOURCE, java.awt.event.KeyEvent.KEY_TYPED, now, 0,
                java.awt.event.KeyEvent.VK_UNDEFINED, '\n');
            m.processKeyTyped(typed);
            consumed = typed.isConsumed();
        }
        m.processKeyReleased(new java.awt.event.KeyEvent(SOURCE, java.awt.event.KeyEvent.KEY_RELEASED, now, 0, vk, '\n'));
        return consumed;
    }

    /** Client thread: an Enter key down went through the listeners and its up is still to come. */
    private static boolean enterDown;

    /** Main thread, from the Window.Callback proxy: true when the event was taken (it is re-dispatched later). */
    static boolean onKey(final KeyEvent e, final Window.Callback game) {
        if (e.isSystem() || e.getAction() == KeyEvent.ACTION_MULTIPLE || !hasListeners()) return false;
        final KeyEvent copy = new KeyEvent(e);
        clientThread.invokeLater(() -> {
            KeyEvent out = copy;
            try {
                out = process(copy);
            } catch (Throwable t) {
                Log.e(Host.TAG, "key listeners threw", t);
            }
            if (out == null) return;
            final KeyEvent send = out;
            MAIN.post(() -> {
                try {
                    game.dispatchKeyEvent(send);
                } catch (Throwable t) {
                    Log.e(Host.TAG, "key re-dispatch failed", t);
                }
            });
        });
        return true;
    }

    private static boolean shiftDown;

    /**
     * Client thread, per cycle: the overlay's Shift toggle (Native.shiftHeld, what isKeyPressed(KC_SHIFT)
     * reports) as VK_SHIFT press/release events for KeyListeners, which desktop plugins use to arm
     * shift-click modes (Line Markers, Ground Markers' hotkey). Listeners only; the game sees no key.
     */
    static void syncShift() {
        boolean held = Native.shiftHeld();
        if (held == shiftDown) return;
        shiftDown = held;
        KeyManager m = manager;
        if (m == null) return;
        int mods = held ? java.awt.event.InputEvent.SHIFT_DOWN_MASK | java.awt.event.InputEvent.SHIFT_MASK : 0;
        java.awt.event.KeyEvent ke = new java.awt.event.KeyEvent(SOURCE,
            held ? java.awt.event.KeyEvent.KEY_PRESSED : java.awt.event.KeyEvent.KEY_RELEASED,
            System.currentTimeMillis(), mods, java.awt.event.KeyEvent.VK_SHIFT, java.awt.event.KeyEvent.CHAR_UNDEFINED);
        try {
            if (held) m.processKeyPressed(ke); else m.processKeyReleased(ke);
        } catch (Throwable t) {
            Log.e(Host.TAG, "shift key listeners threw", t);
        }
    }

    /** Client thread: runs the listeners; the event the game should get, or null when consumed. */
    private static KeyEvent process(KeyEvent e) {
        KeyManager m = manager;
        int vk = toAwt(e.getKeyCode());
        int mods = modifiers(e.getMetaState());
        int uc = e.getUnicodeChar();
        char ch = uc > 0 && (uc & KeyCharacterMapCombining) == 0 ? (char) uc : java.awt.event.KeyEvent.CHAR_UNDEFINED;
        if (vk == 0 && ch == java.awt.event.KeyEvent.CHAR_UNDEFINED) return e;  // nothing a listener could match
        boolean down = e.getAction() == KeyEvent.ACTION_DOWN;
        if (PromptEnter.isEnter(e)) {
            // With the soft keyboard shown the IME turns an Enter's down into the editor action
            // (PromptEnter, which sends the listeners their own press and release); its up still
            // comes here. Listeners get no release without a press.
            if (!down && !enterDown) return e;
            enterDown = down;
        }
        java.awt.event.KeyEvent ke = new java.awt.event.KeyEvent(SOURCE,
            down ? java.awt.event.KeyEvent.KEY_PRESSED : java.awt.event.KeyEvent.KEY_RELEASED,
            System.currentTimeMillis(), mods, vk, ch);
        if (down) m.processKeyPressed(ke); else m.processKeyReleased(ke);
        boolean consumed = ke.isConsumed();
        if (down && !consumed && ch != java.awt.event.KeyEvent.CHAR_UNDEFINED && ke.getKeyChar() != java.awt.event.KeyEvent.CHAR_UNDEFINED) {
            // AWT's KEY_TYPED follows the press of a printable key. On Android the press itself
            // types the character, so a consumed KEY_TYPED drops the press.
            java.awt.event.KeyEvent typed = new java.awt.event.KeyEvent(SOURCE, java.awt.event.KeyEvent.KEY_TYPED,
                System.currentTimeMillis(), mods, java.awt.event.KeyEvent.VK_UNDEFINED, ke.getKeyChar());
            m.processKeyTyped(typed);
            if (typed.isConsumed() && ke.getKeyCode() == vk) consumed = true;
        }
        if (consumed) {
            if ("1".equals(Native.property("debug.osrsrl.keylog"))) Log.i(Host.TAG, "key " + vk + (down ? " down" : " up") + " consumed");
            return null;
        }
        if (ke.getKeyCode() == vk) return e;
        int code = toAndroid(ke.getKeyCode());
        if ("1".equals(Native.property("debug.osrsrl.keylog")))
            Log.i(Host.TAG, "key " + vk + (down ? " down" : " up") + " remapped to " + ke.getKeyCode() + " (android " + code + ")");
        if (code == KeyEvent.KEYCODE_UNKNOWN) return null;
        // Remapped keys carry no character (Key Remapping clears it), so no meta state either.
        return new KeyEvent(e.getDownTime(), e.getEventTime(), e.getAction(), code, e.getRepeatCount(), 0,
            e.getDeviceId(), 0, e.getFlags(), e.getSource());
    }

    private static final int KeyCharacterMapCombining = android.view.KeyCharacterMap.COMBINING_ACCENT;

    private static int modifiers(int meta) {
        int m = 0;
        if ((meta & KeyEvent.META_SHIFT_ON) != 0) m |= java.awt.event.InputEvent.SHIFT_DOWN_MASK | java.awt.event.InputEvent.SHIFT_MASK;
        if ((meta & KeyEvent.META_CTRL_ON) != 0) m |= java.awt.event.InputEvent.CTRL_DOWN_MASK | java.awt.event.InputEvent.CTRL_MASK;
        if ((meta & KeyEvent.META_ALT_ON) != 0) m |= java.awt.event.InputEvent.ALT_DOWN_MASK | java.awt.event.InputEvent.ALT_MASK;
        if ((meta & KeyEvent.META_META_ON) != 0) m |= java.awt.event.InputEvent.META_DOWN_MASK | java.awt.event.InputEvent.META_MASK;
        return m;
    }

    // Android key code <-> AWT virtual key, both ways (letters, digits, F-keys and the usual specials).
    private static final int[][] PAIRS = {
        {KeyEvent.KEYCODE_DPAD_UP, 0x26}, {KeyEvent.KEYCODE_DPAD_DOWN, 0x28}, {KeyEvent.KEYCODE_DPAD_LEFT, 0x25},
        {KeyEvent.KEYCODE_DPAD_RIGHT, 0x27}, {KeyEvent.KEYCODE_ENTER, 0x0A}, {KeyEvent.KEYCODE_ESCAPE, 0x1B},
        {KeyEvent.KEYCODE_DEL, 0x08}, {KeyEvent.KEYCODE_FORWARD_DEL, 0x7F}, {KeyEvent.KEYCODE_TAB, 0x09},
        {KeyEvent.KEYCODE_SPACE, 0x20}, {KeyEvent.KEYCODE_SHIFT_LEFT, 0x10}, {KeyEvent.KEYCODE_SHIFT_RIGHT, 0x10},
        {KeyEvent.KEYCODE_CTRL_LEFT, 0x11}, {KeyEvent.KEYCODE_CTRL_RIGHT, 0x11}, {KeyEvent.KEYCODE_ALT_LEFT, 0x12},
        {KeyEvent.KEYCODE_ALT_RIGHT, 0x12}, {KeyEvent.KEYCODE_MOVE_HOME, 0x24}, {KeyEvent.KEYCODE_MOVE_END, 0x23},
        {KeyEvent.KEYCODE_PAGE_UP, 0x21}, {KeyEvent.KEYCODE_PAGE_DOWN, 0x22}, {KeyEvent.KEYCODE_SLASH, 0x2F},
        {KeyEvent.KEYCODE_COMMA, 0x2C}, {KeyEvent.KEYCODE_PERIOD, 0x2E}, {KeyEvent.KEYCODE_MINUS, 0x2D},
        {KeyEvent.KEYCODE_EQUALS, 0x3D}, {KeyEvent.KEYCODE_SEMICOLON, 0x3B}, {KeyEvent.KEYCODE_LEFT_BRACKET, 0x5B},
        {KeyEvent.KEYCODE_RIGHT_BRACKET, 0x5D}, {KeyEvent.KEYCODE_BACKSLASH, 0x5C}, {KeyEvent.KEYCODE_GRAVE, 0xC0},
        {KeyEvent.KEYCODE_APOSTROPHE, 0xDE},
    };

    static int toAwt(int code) {
        if (code >= KeyEvent.KEYCODE_A && code <= KeyEvent.KEYCODE_Z) return 0x41 + (code - KeyEvent.KEYCODE_A);
        if (code >= KeyEvent.KEYCODE_0 && code <= KeyEvent.KEYCODE_9) return 0x30 + (code - KeyEvent.KEYCODE_0);
        if (code >= KeyEvent.KEYCODE_F1 && code <= KeyEvent.KEYCODE_F12) return 0x70 + (code - KeyEvent.KEYCODE_F1);
        for (int[] p : PAIRS) if (p[0] == code) return p[1];
        return 0;
    }

    static int toAndroid(int vk) {
        if (vk >= 0x41 && vk <= 0x5A) return KeyEvent.KEYCODE_A + (vk - 0x41);
        if (vk >= 0x30 && vk <= 0x39) return KeyEvent.KEYCODE_0 + (vk - 0x30);
        if (vk >= 0x70 && vk <= 0x7B) return KeyEvent.KEYCODE_F1 + (vk - 0x70);
        for (int[] p : PAIRS) if (p[1] == vk) return p[0];
        return KeyEvent.KEYCODE_UNKNOWN;
    }
}
