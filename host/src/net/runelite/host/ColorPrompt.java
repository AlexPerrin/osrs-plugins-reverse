package net.runelite.host;

import android.app.Activity;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.GridLayout;
import android.widget.LinearLayout;

import java.awt.Color;
import java.util.function.Consumer;

/**
 * Android stand-in for RuneLite's Swing colour picker: a dialog with a hex field, a preview and
 * a grid of swatches. The result is delivered on the game thread (the host's Swing EDT).
 */
public final class ColorPrompt {
    private ColorPrompt() {}

    private static final int[] SWATCHES = {
        0xFFFFFF, 0xC0C0C0, 0x808080, 0x404040, 0x000000, 0xFF0000,
        0xFF8000, 0xFFFF00, 0x80FF00, 0x00FF00, 0x00FF80, 0x00FFFF,
        0x0080FF, 0x0000FF, 0x8000FF, 0xFF00FF, 0xFF0080, 0x800000,
        0x804000, 0x808000, 0x008000, 0x008080, 0x000080, 0x800080,
    };

    /** Shows the dialog; `done` runs on the game thread with the picked colour (not on Cancel). */
    public static void show(String title, Color initial, boolean alpha, Consumer<Color> done) {
        new Handler(Looper.getMainLooper()).post(() -> {
            try {
                Activity a = Sidebar.currentActivity();
                if (a == null) return;
                float dp = a.getResources().getDisplayMetrics().density;
                LinearLayout root = new LinearLayout(a);
                root.setOrientation(LinearLayout.VERTICAL);
                int pad = (int) (16 * dp);
                root.setPadding(pad, pad / 2, pad, 0);

                View preview = new View(a);
                root.addView(preview, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, (int) (32 * dp)));
                EditText hex = new EditText(a);
                hex.setSingleLine(true);
                root.addView(hex);
                hex.addTextChangedListener(new TextWatcher() {
                    @Override public void beforeTextChanged(CharSequence s, int st, int c, int af) {}
                    @Override public void onTextChanged(CharSequence s, int st, int b, int c) {}
                    @Override public void afterTextChanged(Editable s) {
                        Color c = parse(s.toString(), alpha);
                        if (c != null) preview.setBackgroundColor(c.getRGB());
                    }
                });
                hex.setText(format(initial, alpha));

                GridLayout grid = new GridLayout(a);
                grid.setColumnCount(6);
                int cell = (int) (36 * dp), margin = (int) (3 * dp);
                for (int rgb : SWATCHES) {
                    View sw = new View(a);
                    GradientDrawable d = new GradientDrawable();
                    d.setColor(0xFF000000 | rgb);
                    d.setStroke(1, 0xFF808080);
                    sw.setBackground(d);
                    GridLayout.LayoutParams lp = new GridLayout.LayoutParams();
                    lp.width = cell;
                    lp.height = cell;
                    lp.setMargins(margin, margin, margin, margin);
                    sw.setOnClickListener(v -> {
                        Color cur = parse(hex.getText().toString(), alpha);
                        int al = alpha && cur != null ? cur.getAlpha() : 255;
                        hex.setText(format(new Color(rgb >> 16 & 0xff, rgb >> 8 & 0xff, rgb & 0xff, al), alpha));
                    });
                    grid.addView(sw, lp);
                }
                root.addView(grid);

                new android.app.AlertDialog.Builder(a)
                    .setTitle(title)
                    .setView(root)
                    .setPositiveButton("OK", (dlg, w) -> {
                        Color c = parse(hex.getText().toString(), alpha);
                        if (c == null) return;
                        Log.i(Host.TAG, "colour picked for '" + title + "': " + format(c, true));
                        javax.swing.SwingUtilities.invokeLater(() -> done.accept(c));
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
            } catch (Throwable t) {
                Log.e(Host.TAG, "colour prompt failed", t);
            }
        });
    }

    static String format(Color c, boolean alpha) {
        return alpha ? String.format("#%08X", c.getRGB()) : String.format("#%06X", c.getRGB() & 0xffffff);
    }

    /** "#RRGGBB" or "#AARRGGBB" (alpha only when allowed), with or without '#'. Null if invalid. */
    static Color parse(String s, boolean alpha) {
        s = s.trim();
        if (s.startsWith("#")) s = s.substring(1);
        try {
            if (s.length() == 6) return new Color(Integer.parseInt(s, 16));
            if (s.length() == 8 && alpha) return new Color((int) Long.parseLong(s, 16), true);
        } catch (NumberFormatException e) {
            // invalid
        }
        return null;
    }
}
