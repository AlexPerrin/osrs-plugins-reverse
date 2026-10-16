package net.runelite.host;

import android.app.Activity;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.MotionEvent;
import android.view.Window;
import java.awt.Component;
import java.awt.Container;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import javax.swing.AbstractButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JScrollPane;
import javax.swing.JToggleButton;
import javax.swing.text.JTextComponent;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;

/**
 * The plugin sidebar: RuneLite's navigation buttons and the selected plugin panel (headless Swing)
 * shown by the native overlay (overlay.cpp) as an ImGui window.
 *
 * The panel's component tree is flattened into lines "kind\tdepth\tcolor\ttext" (kinds: L label,
 * B button, C checkbox (text prefixed with 0/1), T text field, X combo box, H clickable panel)
 * after a header line "S\tselected\ttooltip...". The overlay sends back taps as (kind, index)
 * pairs, which are dispatched to the Swing listeners on the game thread.
 */
final class Sidebar {
    private static final String TAG = Host.TAG;
    private static ClientToolbar toolbar;
    private static int selected = 0;
    private static final List<Component> nodes = new ArrayList<>();
    private static String lastModel = "";
    private static int cycles;
    private static Field scrollView;

    private Sidebar() {}

    /** Debug: the current model is written here (files/sidebar-model.txt) when it changes. */
    static java.io.File dumpFile;

    static void start(ClientToolbar tb, java.io.File dir) {
        toolbar = tb;
        if ("1".equals(Native.property("debug.osrsrl.sbdump"))) dumpFile = new java.io.File(dir, "sidebar-model.txt");
        installTouchRouter(0);
    }

    /** Game thread, every client cycle. */
    static void onCycle() {
        runSwingQueue();
        if (toolbar == null) return;
        int[] ev = Native.sidebarPollEvents();
        boolean dirty = false;
        if (ev != null) {
            for (int i = 0; i + 1 < ev.length; i += 2) {
                try {
                    handle(ev[i], ev[i + 1]);
                } catch (Throwable t) {
                    Log.e(TAG, "sidebar event failed", t);
                }
                dirty = true;
            }
        }
        if (!dirty && ++cycles % 10 != 0) return;
        if (!Native.sidebarOpen() && !dirty && cycles % 100 != 0) return;
        try {
            String model = build();
            autoClick();
            if (!model.equals(lastModel)) {
                lastModel = model;
                Native.sidebarSetModel(model);
                if (dumpFile != null) {
                    try (java.io.FileWriter w = new java.io.FileWriter(dumpFile)) {
                        w.write(model);
                    } catch (java.io.IOException ignored) {
                    }
                }
            }
        } catch (Throwable t) {
            Log.e(TAG, "sidebar model failed", t);
        }
    }

    private static String autoClickDone;

    /**
     * Test hook: `setprop debug.osrsrl.sbclick "<text>"` taps the first button whose text, or
     * whose preceding label's text, equals <text> (once per value; e.g. a quest name in Quest Helper).
     */
    private static void autoClick() {
        String want = Native.property("debug.osrsrl.sbclick");
        if (want.isEmpty() || want.equals(autoClickDone)) return;
        for (int i = 0; i < nodes.size(); i++) {
            Component c = nodes.get(i);
            if (!(c instanceof AbstractButton)) continue;
            boolean match = want.equals(text(c)) || (i > 0 && nodes.get(i - 1) instanceof JLabel && want.equals(text(nodes.get(i - 1))));
            if (match) {
                autoClickDone = want;
                Log.i(TAG, "sidebar auto-click '" + want + "'");
                handle(2, i);
                return;
            }
        }
    }

    /** Runs what plugins posted with SwingUtilities.invokeLater (the game thread acts as the EDT). */
    static void runSwingQueue() {
        javax.swing.SwingUtilities.__edt = Thread.currentThread();
        for (int i = 0; i < 1000; i++) {
            Runnable r = javax.swing.SwingUtilities.__queue.poll();
            if (r == null) break;
            try {
                r.run();
            } catch (Throwable t) {
                Log.e(TAG, "Swing task threw", t);
            }
        }
    }

    private static void handle(int kind, int index) {
        if (kind == 1) {
            selected = index;
            return;
        }
        if (kind != 2 || index < 0 || index >= nodes.size()) return;
        Component c = nodes.get(index);
        Log.i(TAG, "sidebar tap on " + c.getClass().getName() + " '" + text(c) + "'");
        if (c instanceof AbstractButton) {
            AbstractButton b = (AbstractButton) c;
            if (b instanceof JToggleButton) b.setSelected(!b.isSelected());
            for (java.awt.event.ActionListener l : b.getActionListeners()) l.actionPerformed(new java.awt.event.ActionEvent());
            return;
        }
        long now = System.currentTimeMillis();
        for (java.awt.event.MouseListener l : c.getMouseListeners()) {
            l.mousePressed(new java.awt.event.MouseEvent(c, 501, now, 0, 1, 1, 1, false, 1));
            l.mouseReleased(new java.awt.event.MouseEvent(c, 502, now, 0, 1, 1, 1, false, 1));
            l.mouseClicked(new java.awt.event.MouseEvent(c, 500, now, 0, 1, 1, 1, false, 1));
        }
    }

    private static String build() {
        nodes.clear();
        StringBuilder sb = new StringBuilder();
        List<NavigationButton> buttons = toolbar.getButtons();
        if (selected >= buttons.size()) selected = 0;
        sb.append("S\t").append(selected);
        for (NavigationButton b : buttons) sb.append('\t').append(clean(b.getTooltip()));
        sb.append('\n');
        if (!buttons.isEmpty() && buttons.get(selected).getPanel() != null) walk(buttons.get(selected).getPanel(), 0, sb);
        return sb.toString();
    }

    private static void walk(Component c, int depth, StringBuilder sb) {
        if (c == null || !c.isVisible() || nodes.size() > 3000) return;
        if (c instanceof JLabel) {
            String t = clean(((JLabel) c).getText());
            if (!t.isEmpty()) add(sb, 'L', depth, c, t);
            return;
        }
        if (c instanceof JCheckBox) {
            add(sb, 'C', depth, c, (((JCheckBox) c).isSelected() ? "1" : "0") + text(c));
            return;
        }
        if (c instanceof AbstractButton) {
            add(sb, 'B', depth, c, text(c));
            return;
        }
        if (c instanceof JTextComponent) {
            add(sb, 'T', depth, c, clean(((JTextComponent) c).getText()));
            return;
        }
        if (c instanceof JComboBox) {
            Object sel = ((JComboBox) c).getSelectedItem();
            add(sb, 'X', depth, c, sel != null ? clean(sel.toString()) : "");
            return;
        }
        if (c instanceof JScrollPane) {
            walk(scrollPaneView((JScrollPane) c), depth, sb);
            return;
        }
        if (c.getMouseListeners().length > 0) {
            String t = labelText(c, new StringBuilder(), 0).toString().trim();
            if (!t.isEmpty()) add(sb, 'H', depth, c, t);
        }
        if (c instanceof Container) {
            Container k = (Container) c;
            if (k.getLayout() instanceof java.awt.CardLayout) {
                walk(((java.awt.CardLayout) k.getLayout()).__visibleCard(k), depth + 1, sb);
                return;
            }
            for (Component child : k.getComponents()) walk(child, depth + 1, sb);
        }
    }

    private static StringBuilder labelText(Component c, StringBuilder out, int found) {
        if (out.length() > 80 || c == null || !c.isVisible()) return out;
        if (c instanceof JLabel) {
            String t = clean(((JLabel) c).getText());
            if (!t.isEmpty()) out.append(out.length() > 0 ? "  " : "").append(t);
        } else if (c instanceof Container) {
            for (Component child : ((Container) c).getComponents()) labelText(child, out, found);
        }
        return out;
    }

    private static void add(StringBuilder sb, char kind, int depth, Component c, String text) {
        nodes.add(c);
        int color = 0;
        try {
            java.awt.Color fg = c.getForeground();
            if (fg != null && fg != java.awt.Color.BLACK) color = fg.getRGB();
        } catch (Throwable ignored) {
        }
        sb.append(kind).append('\t').append(depth).append('\t').append(Integer.toHexString(color)).append('\t').append(text).append('\n');
    }

    private static String text(Component c) {
        if (c instanceof AbstractButton) {
            String t = clean(((AbstractButton) c).getText());
            if (t.isEmpty() && c instanceof javax.swing.JComponent) t = clean(((javax.swing.JComponent) c).getToolTipText());
            return t.isEmpty() ? ">" : t;
        }
        if (c instanceof JLabel) return clean(((JLabel) c).getText());
        return c.getClass().getSimpleName();
    }

    private static Component scrollPaneView(JScrollPane p) {
        try {
            if (scrollView == null) {
                scrollView = JScrollPane.class.getDeclaredField("__ViewportView");
                scrollView.setAccessible(true);
            }
            Object v = scrollView.get(p);
            if (v instanceof Component) return (Component) v;
        } catch (Throwable ignored) {
        }
        Component[] cs = p.getViewport().getComponents();
        return cs.length > 0 ? cs[0] : null;
    }

    /** Strips HTML and the separators the model format uses. */
    static String clean(String s) {
        if (s == null) return "";
        if (s.indexOf('<') >= 0) {
            s = s.replaceAll("(?i)<br\\s*/?>", " ").replaceAll("<[^>]*>", "").replace("&nbsp;", " ")
                    .replace("&lt;", "<").replace("&gt;", ">").replace("&amp;", "&");
        }
        return s.replace('\t', ' ').replace('\n', ' ').trim();
    }

    // --- touch routing: taps on the sidebar go to the overlay instead of the game ---

    private static void installTouchRouter(final int attempt) {
        final Handler main = new Handler(Looper.getMainLooper());
        main.postDelayed(() -> {
            try {
                Activity a = currentActivity();
                if (a == null || a.getWindow() == null) {
                    if (attempt < 60) installTouchRouter(attempt + 1);
                    return;
                }
                final Window w = a.getWindow();
                final Window.Callback orig = w.getCallback();
                InvocationHandler h = (proxy, m, args) -> {
                    if (m.getName().equals("dispatchTouchEvent") && args != null && args[0] instanceof MotionEvent) {
                        MotionEvent e = (MotionEvent) args[0];
                        if (Native.overlayTouch(e.getActionMasked(), e.getX(), e.getY())) return true;
                    }
                    try {
                        return m.invoke(orig, args);
                    } catch (InvocationTargetException ex) {
                        throw ex.getCause();
                    }
                };
                w.setCallback((Window.Callback) Proxy.newProxyInstance(Sidebar.class.getClassLoader(), new Class<?>[] {Window.Callback.class}, h));
                Log.i(TAG, "sidebar touch router installed on " + a.getClass().getName());
            } catch (Throwable t) {
                Log.e(TAG, "sidebar touch router failed", t);
            }
        }, attempt == 0 ? 500 : 2000);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Activity currentActivity() throws Exception {
        Class<?> at = Class.forName("android.app.ActivityThread");
        Object thread = at.getMethod("currentActivityThread").invoke(null);
        Field f = at.getDeclaredField("mActivities");
        f.setAccessible(true);
        Map records = (Map) f.get(thread);
        for (Object r : records.values()) {
            Field af = r.getClass().getDeclaredField("activity");
            af.setAccessible(true);
            Activity a = (Activity) af.get(r);
            if (a != null && !a.isFinishing()) return a;
        }
        return null;
    }
}
