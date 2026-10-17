package net.runelite.host;

import android.app.Activity;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.util.function.Consumer;
import java.util.function.IntConsumer;

/**
 * Android dialogs that stand in for RuneLite's chatbox inputs (ChatboxTextInput, ChatboxTextMenuInput).
 * The dialogs run on the UI thread; every callback is posted to the game thread through the
 * headless Swing queue (Sidebar drains it each client cycle).
 */
public final class AndroidPrompts {
    private AndroidPrompts() {}

    private static void onGame(Runnable r) { javax.swing.SwingUtilities.invokeLater(r); }

    /**
     * A text field with the soft keyboard. `onChanged` gets each edit, `onOk` the text when OK is
     * pressed, `onClosed` runs once when the dialog goes away (OK, Cancel or back).
     */
    public static void text(String prompt, String value, int lines, Consumer<String> onChanged,
                            Consumer<String> onOk, Runnable onClosed) {
        new Handler(Looper.getMainLooper()).post(() -> {
            try {
                Activity a = Sidebar.currentActivity();
                if (a == null) {
                    if (onClosed != null) onGame(onClosed);
                    return;
                }
                final android.widget.EditText edit = new android.widget.EditText(a);
                if (lines > 1) {
                    edit.setSingleLine(false);
                    edit.setMinLines(Math.min(lines, 4));
                } else {
                    edit.setSingleLine(true);
                }
                edit.setText(value != null ? value : "");
                edit.setSelection(edit.getText().length());
                if (onChanged != null) {
                    edit.addTextChangedListener(new android.text.TextWatcher() {
                        @Override public void beforeTextChanged(CharSequence s, int st, int c, int af) {}
                        @Override public void onTextChanged(CharSequence s, int st, int b, int c) {}
                        @Override public void afterTextChanged(android.text.Editable s) {
                            String t = s.toString();
                            onGame(() -> onChanged.accept(t));
                        }
                    });
                }
                android.app.AlertDialog d = new android.app.AlertDialog.Builder(a)
                    .setTitle(Sidebar.clean(prompt))  // chatbox prompts carry <br>/<col> tags (Friend Notes)
                    .setView(edit)
                    .setPositiveButton("OK", (dlg, w) -> {
                        String t = edit.getText().toString();
                        if (onOk != null) onGame(() -> onOk.accept(t));
                    })
                    .setNegativeButton("Cancel", null)
                    .create();
                d.setOnDismissListener(dlg -> { if (onClosed != null) onGame(onClosed); });
                d.getWindow().setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
                d.show();
                edit.requestFocus();
            } catch (Throwable t) {
                Log.e(Host.TAG, "text prompt failed", t);
            }
        });
    }

    /** A list of options; `onPick` gets the chosen index, `onClosed` runs once when it goes away. */
    public static void menu(String title, String[] options, IntConsumer onPick, Runnable onClosed) {
        new Handler(Looper.getMainLooper()).post(() -> {
            try {
                Activity a = Sidebar.currentActivity();
                if (a == null) {
                    if (onClosed != null) onGame(onClosed);
                    return;
                }
                android.app.AlertDialog d = new android.app.AlertDialog.Builder(a)
                    .setTitle(Sidebar.clean(title))
                    .setItems(options, (dlg, which) -> onGame(() -> onPick.accept(which)))
                    .setNegativeButton("Cancel", null)
                    .create();
                d.setOnDismissListener(dlg -> { if (onClosed != null) onGame(onClosed); });
                d.show();
            } catch (Throwable t) {
                Log.e(Host.TAG, "menu prompt failed", t);
            }
        });
    }
}
