package net.runelite.host;

import android.util.Log;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import net.runelite.client.plugins.Plugin;

/**
 * Debug hook: `setprop debug.osrsrl.probe "QuestHelperPlugin.getSelectedQuest().getCurrentStep().getActiveStep()"`
 * evaluates the path every few game ticks on the game thread and logs the result's class and
 * fields (tag osrsrl-probe). The first segment is a running plugin's simple class name; then
 * `name()` calls a no-arg method and `name` reads a field (private members included). Empty = off.
 */
final class Probe {
    private static final String TAG = "osrsrl-probe";
    private static List<Plugin> plugins;
    private static int tick;

    static void init(List<Plugin> running) {
        plugins = running;
    }

    static void onGameTick() {
        if ("1".equals(Native.property("debug.osrsrl.camlog"))) logCamera();
        if (plugins == null || (tick++ % 5) != 0) return;
        String path = Native.property("debug.osrsrl.probe");
        if (path.isEmpty()) return;
        try {
            Object o = eval(path);
            Log.i(TAG, path + " = " + describe(o));
        } catch (Throwable t) {
            Log.w(TAG, path + " failed: " + t);
        }
    }

    private static Object eval(String path) throws Exception {
        String[] seg = path.split("\\.");
        Object cur = null;
        for (Plugin p : plugins) if (p.getClass().getSimpleName().equals(seg[0])) cur = p;
        if (cur == null) throw new IllegalArgumentException("no plugin " + seg[0]);
        for (int i = 1; i < seg.length && cur != null; i++) {
            String s = seg[i];
            if (s.endsWith("()")) {
                Method m = findMethod(cur.getClass(), s.substring(0, s.length() - 2));
                m.setAccessible(true);
                cur = m.invoke(cur);
            } else {
                Field f = findField(cur.getClass(), s);
                f.setAccessible(true);
                cur = f.get(cur);
            }
        }
        return cur;
    }

    private static Method findMethod(Class<?> c, String name) throws NoSuchMethodException {
        for (Class<?> k = c; k != null; k = k.getSuperclass())
            for (Method m : k.getDeclaredMethods())
                if (m.getName().equals(name) && m.getParameterTypes().length == 0) return m;
        throw new NoSuchMethodException(c.getName() + "." + name + "()");
    }

    private static Field findField(Class<?> c, String name) throws NoSuchFieldException {
        for (Class<?> k = c; k != null; k = k.getSuperclass())
            for (Field f : k.getDeclaredFields()) if (f.getName().equals(name)) return f;
        throw new NoSuchFieldException(c.getName() + "." + name);
    }

    private static String describe(Object o) {
        if (o == null) return "null";
        StringBuilder sb = new StringBuilder(o.getClass().getName()).append(" {");
        for (Class<?> k = o.getClass(); k != null && k != Object.class; k = k.getSuperclass()) {
            for (Field f : k.getDeclaredFields()) {
                if (Modifier.isStatic(f.getModifiers())) continue;
                try {
                    f.setAccessible(true);
                    sb.append("\n  ").append(f.getName()).append('=').append(brief(f.get(o)));
                } catch (Throwable t) {
                    sb.append("\n  ").append(f.getName()).append("=<").append(t).append('>');
                }
            }
        }
        return sb.append("\n}").toString();
    }

    private static String brief(Object v) {
        if (v == null) return "null";
        if (v instanceof Collection) {
            Collection<?> c = (Collection<?>) v;
            StringBuilder sb = new StringBuilder("size " + c.size() + " [");
            int n = 0;
            for (Object e : c) {
                if (n++ == 3) { sb.append(", ..."); break; }
                sb.append(n > 1 ? ", " : "").append(oneLine(e));
            }
            return sb.append(']').toString();
        }
        if (v instanceof Map) return "map size " + ((Map<?, ?>) v).size();
        return oneLine(v);
    }

    private static String oneLine(Object v) {
        String s;
        try {
            s = String.valueOf(v);
        } catch (Throwable t) {
            s = v.getClass().getName() + "<toString threw>";
        }
        return s.length() > 160 ? s.substring(0, 160) + "..." : s;
    }

    /** `setprop debug.osrsrl.camlog 1`: camera vs player frames, for projection offsets. */
    private static void logCamera() {
        try {
            int[] cam = Native.camera(), pos = Native.playerPosition();
            if (cam == null || pos == null) return;
            net.runelite.api.Client c = Host.client();
            AndroidWorldView wv = Host.worldView();
            net.runelite.api.coords.LocalPoint lp = new net.runelite.api.coords.LocalPoint(pos[4], pos[5], net.runelite.api.WorldView.TOPLEVEL);
            net.runelite.api.Point p = net.runelite.api.Perspective.localToCanvas(c, lp, wv.getPlane());
            Log.i(TAG, "cam " + java.util.Arrays.toString(cam) + " player world " + pos[0] + "," + pos[1]
                + " tile " + pos[2] + "," + pos[3] + " fine " + pos[4] + "," + pos[5]
                + " focal-fine " + (cam[5] - pos[4]) + "," + (cam[6] - pos[5])
                + " base " + wv.getBaseX() + "," + wv.getBaseY()
                + " h " + wv.getTileHeights()[wv.getPlane()][pos[2]][pos[3]]
                + " hgame " + c.getTopLevelWorldView().getTileHeight(pos[4], pos[5], wv.getPlane())
                + " canvas " + p + " of " + c.getViewportWidth() + "x" + c.getViewportHeight());
            if ((tick % 10) == 0) {
                int root = Native.rootInterface(), n = Native.widgetChildCount(root);
                StringBuilder sb = new StringBuilder("root " + root + ":");
                for (int i = 0; i < n; i++) {
                    AndroidWidget w = new AndroidWidget(root << 16 | i);
                    if (w.rawGeo() == null || w.isHidden() || w.getWidth() <= 0) continue;
                    java.awt.Rectangle r = w.getBounds();
                    sb.append(' ').append(i).append('=').append(r.x).append(',').append(r.y).append(' ').append(r.width).append('x').append(r.height);
                }
                Log.i(TAG, sb.toString());
            }
        } catch (Throwable t) {
            Log.w(TAG, "camlog: " + t);
        }
    }
}
