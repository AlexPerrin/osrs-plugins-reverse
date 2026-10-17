package net.runelite.host;

import android.app.Activity;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * JOptionPane's static dialogs (headless javax.swing.JOptionPane.__dialog) as modal Android
 * dialogs. Swing's dialogs block the caller until answered; so do these: the calling thread (the
 * game thread, from a sidebar tap or a plugin callback) waits while the dialog is up, which
 * freezes the game frames behind it like a modal window. On the Android UI thread they can't
 * block, so they answer at once as cancelled.
 *
 * Kinds (the generated JOptionPane passes {message, title, options, initial, optionType}):
 * input (String or null), inputSelect (one of the options or null), confirm (YES 0 / NO 1 /
 * CANCEL 2 / closed -1; OK_CANCEL: OK 0 / CANCEL 2), option (index or -1), message (OK only,
 * not waited for).
 */
final class ModalDialogs {
    private static final String TAG = "osrsrl-host";
    private static final long TIMEOUT_MIN = 10;

    private ModalDialogs() {}

    static void install() {
        javax.swing.JOptionPane.__dialog = ModalDialogs::show;
    }

    private static Object show(String kind, Object[] a) {
        String message = text(a[0]), title = a[1] != null ? String.valueOf(a[1]) : null;
        Object[] options = (Object[]) a[2];
        Object initial = a[3];
        int optionType = a[4] instanceof Integer ? (Integer) a[4] : -1;
        Log.i(TAG, "JOptionPane " + kind + ": " + (title != null ? title + ": " : "") + message);
        if ("message".equals(kind)) {
            post(dialog -> dialog.setPositiveButton("OK", null), title, message, null);
            return null;
        }
        if (Looper.myLooper() == Looper.getMainLooper()) return cancelled(kind);
        final AtomicReference<Object> result = new AtomicReference<>(cancelled(kind));
        final CountDownLatch done = new CountDownLatch(1);
        switch (kind) {
            case "input": {
                final android.widget.EditText[] edit = new android.widget.EditText[1];
                post((KeyboardSetup) b -> {
                    edit[0] = new android.widget.EditText(b.getContext());
                    edit[0].setSingleLine(true);
                    edit[0].setText(initial != null ? String.valueOf(initial) : "");
                    edit[0].setSelection(edit[0].getText().length());
                    b.setView(edit[0])
                        .setPositiveButton("OK", (d, w) -> result.set(edit[0].getText().toString()))
                        .setNegativeButton("Cancel", null);
                }, title, message, done);
                break;
            }
            case "inputSelect":
            case "option": {
                final Object[] opts = options != null ? options : new Object[0];
                final String[] labels = new String[opts.length];
                for (int i = 0; i < opts.length; i++) labels[i] = text(opts[i]);
                final boolean index = "option".equals(kind);
                // A list dialog can't show a message too: the message becomes the title.
                String head = message.isEmpty() ? title : title != null ? title + ": " + message : message;
                post(b -> b.setItems(labels, (d, w) -> result.set(index ? (Object) w : opts[w]))
                    .setNegativeButton("Cancel", null), head, null, done);
                break;
            }
            case "confirm": {
                // YES_NO 0, YES_NO_CANCEL 1, OK_CANCEL 2; default (-1) behaves as YES_NO_CANCEL.
                post(b -> {
                    if (optionType == 2) {
                        b.setPositiveButton("OK", (d, w) -> result.set(0)).setNegativeButton("Cancel", (d, w) -> result.set(2));
                    } else {
                        b.setPositiveButton("Yes", (d, w) -> result.set(0)).setNegativeButton("No", (d, w) -> result.set(1));
                        if (optionType != 0) b.setNeutralButton("Cancel", (d, w) -> result.set(2));
                    }
                }, title, message, done);
                break;
            }
            default:
                return null;
        }
        try {
            if (!done.await(TIMEOUT_MIN, TimeUnit.MINUTES)) Log.w(TAG, "JOptionPane " + kind + " timed out");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return result.get();
    }

    private static Object cancelled(String kind) {
        return "confirm".equals(kind) || "option".equals(kind) ? (Object) (-1) : null;
    }

    private static String text(Object o) {
        if (o == null) return "";
        if (o instanceof javax.swing.JLabel) return Sidebar.clean(((javax.swing.JLabel) o).getText());
        return Sidebar.clean(String.valueOf(o));
    }

    private interface Setup { void apply(android.app.AlertDialog.Builder b); }
    /** A dialog with a text field: shown with the soft keyboard up. */
    private interface KeyboardSetup extends Setup {}

    /** Shows a dialog on the UI thread; `done` counts down when it goes away (after its button handler). */
    private static void post(Setup setup, String title, String message, CountDownLatch done) {
        new Handler(Looper.getMainLooper()).post(() -> {
            try {
                Activity a = Sidebar.currentActivity();
                if (a == null) { if (done != null) done.countDown(); return; }
                android.app.AlertDialog.Builder b = new android.app.AlertDialog.Builder(a);
                if (title != null) b.setTitle(title);
                if (message != null && !message.isEmpty()) b.setMessage(message);
                setup.apply(b);
                android.app.AlertDialog d = b.create();
                if (b.getContext() != null && setup instanceof KeyboardSetup)
                    d.getWindow().setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
                if (done != null) d.setOnDismissListener(x -> done.countDown());
                d.show();
            } catch (Throwable t) {
                Log.e(TAG, "JOptionPane dialog failed", t);
                if (done != null) done.countDown();
            }
        });
    }
}
