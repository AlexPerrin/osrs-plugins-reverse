package net.runelite.host;

import android.app.Activity;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.widget.TextView;
import java.lang.reflect.Field;

/**
 * The soft keyboard's Enter for RuneLite KeyListeners. While the game's keyboard is shown, its input
 * goes through a hidden EditText (com.jagex.android.a$a, made per show by AndroidKeyboard.show). The
 * IME's Enter (the on-screen Done key, and on BlueStacks also a hardware Enter's key down) is an
 * editor action IME_ACTION_DONE: the EditText's listener (com.jagex.android.a.onEditorAction) hands the
 * game a key 66 through com.jagex.android.t.a -> b.d/b.e (natives ru.hx/ru.wv), and the game submits the
 * chatbox input or prompt then. That key never passes the Window.Callback, so KeyRouter can't run
 * the listeners first (Decimal Prices rewrites the prompt text, varcstr 359, on VK_ENTER).
 *
 * Here each new keyboard EditText, when it takes focus, gets a listener that wraps the game's: a Done
 * action is held, VK_ENTER is run through KeyManager on the client thread (pressed, typed '\n',
 * released, as desktop), and then the game's listener runs on the main thread, once, unless a listener
 * consumed the press. Without KeyListeners the game's listener runs at once, as before. Other action
 * codes and hardware Enters that reach TextView (they carry a KeyEvent and went through KeyRouter
 * already) pass straight through.
 */
final class PromptEnter {
    private PromptEnter() {}

    private static final int IME_ACTION_DONE = 6;
    private static View wrapped;

    /** Main thread, once the touch router is on the Activity's window. */
    static void install(Activity a) {
        View decor = a.getWindow().getDecorView();
        decor.getViewTreeObserver().addOnGlobalFocusChangeListener((oldFocus, newFocus) -> {
            if (newFocus instanceof TextView && newFocus != wrapped && newFocus.getClass().getName().startsWith("com.jagex.android.a$")) {
                wrap((TextView) newFocus);
            }
        });
        Log.i(Host.TAG, "prompt enter: focus listener installed");
    }

    private static void wrap(final TextView et) {
        final TextView.OnEditorActionListener game = gameListener(et);
        if (game == null) {
            Log.w(Host.TAG, "prompt enter: no editor action listener on " + et.getClass().getName());
            return;
        }
        wrapped = et;
        et.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId != IME_ACTION_DONE || event != null || !KeyRouter.listenersActive()) return game.onEditorAction(v, actionId, event);
            KeyRouter.editorEnter(() -> game.onEditorAction(v, actionId, null));
            return false;  // what the game's listener returns: TextView then hides the IME for Done
        });
    }

    /** The game's listener is the EditText's outer com.jagex.android.a (it implements OnEditorActionListener). */
    private static TextView.OnEditorActionListener gameListener(TextView et) {
        for (Class<?> c = et.getClass(); c != null && c != TextView.class; c = c.getSuperclass()) {
            for (Field f : c.getDeclaredFields()) {
                if (!TextView.OnEditorActionListener.class.isAssignableFrom(f.getType())) continue;
                try {
                    f.setAccessible(true);
                    Object o = f.get(et);
                    if (o != null) return (TextView.OnEditorActionListener) o;
                } catch (Throwable t) {
                    Log.w(Host.TAG, "prompt enter: " + t);
                }
            }
        }
        return null;
    }

    static boolean isEnter(KeyEvent e) {
        return e.getKeyCode() == KeyEvent.KEYCODE_ENTER || e.getKeyCode() == KeyEvent.KEYCODE_NUMPAD_ENTER;
    }
}
