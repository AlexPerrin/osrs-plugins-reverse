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
    private static String lastSay = "";

    private static String lastSet = "";
    private static String lastWdump = "";

    private static void dumpGroup(net.runelite.api.Client c, String arg) {
        try {
            int group = Integer.parseInt(arg);
            for (int child = 0; child < 1024; child++) {
                net.runelite.api.widgets.Widget w = c.getWidget(group, child);
                if (w == null) { if (child > 0) break; else continue; }
                Log.i(TAG, "w " + group + ":" + child + " type " + w.getType() + " text '" + w.getText() + "' name '" + w.getName()
                    + "' verb '" + w.getTargetVerb() + "' ops " + java.util.Arrays.toString(w.getActions()) + " sprite " + w.getSpriteId()
                    + " tiling " + w.getSpriteTiling() + " border " + w.getBorderType() + " flip " + w.isFlippedHorizontally() + "/" + w.isFlippedVertically()
                    + " filled " + w.isFilled() + " font " + w.getFontId() + " align " + w.getXTextAlignment() + "/" + w.getYTextAlignment()
                    + " lineh " + w.getLineHeight() + " shadow " + w.getTextShadowed() + " colour " + Integer.toHexString(w.getTextColor())
                    + " item " + w.getItemId() + " orig " + w.getOriginalX() + "," + w.getOriginalY() + " " + w.getOriginalWidth() + "x" + w.getOriginalHeight()
                    + " modes " + w.getXPositionMode() + w.getYPositionMode() + w.getWidthMode() + w.getHeightMode() + " geo " + w.getRelativeX() + "," + w.getRelativeY() + " " + w.getWidth() + "x" + w.getHeight());
            }
        } catch (Throwable t) {
            Log.w(TAG, "wdump " + arg + " failed: " + t);
        }
    }

    private static void testSetter(net.runelite.api.Client c, String[] a) {
        if (a.length < 3) return;
        try {
            int colon = a[1].indexOf(':');
            int id = colon < 0 ? Integer.parseInt(a[1])
                : Integer.parseInt(a[1].substring(0, colon)) << 16 | Integer.parseInt(a[1].substring(colon + 1));
            switch (a[0]) {
                case "whide": {  // "whide <group:child> 0|1"
                    net.runelite.api.widgets.Widget w = c.getWidget(id);
                    if (w == null) { Log.w(TAG, "whide: no widget " + a[1]); break; }
                    boolean before = w.isSelfHidden();
                    w.setHidden(!"0".equals(a[2]));
                    Log.i(TAG, "set hidden " + a[1] + " " + before + " -> " + w.isSelfHidden());
                    break;
                }
                case "wtext": {  // "wtext <group:child> text"
                    net.runelite.api.widgets.Widget w = c.getWidget(id);
                    if (w == null) { Log.w(TAG, "wtext: no widget " + a[1]); break; }
                    String before = w.getText();
                    w.setText(a[2]);
                    Log.i(TAG, "set text " + a[1] + " '" + before + "' -> '" + w.getText() + "' type " + w.getType());
                    break;
                }
                case "iparam": {  // "iparam <item id> <param>": item param as int and string
                    int key = Integer.parseInt(a[2]);
                    net.runelite.api.ItemComposition ic = c.getItemDefinition(id);
                    Log.i(TAG, "item " + id + " param " + key + " int " + ic.getIntValue(key) + " string '" + ic.getStringValue(key) + "'");
                    break;
                }
                case "sound": {  // "sound <id> <volume>"
                    c.playSoundEffect(id, Integer.parseInt(a[2]));
                    Log.i(TAG, "playSoundEffect " + id);
                    break;
                }
                case "varbit": {
                    int before = c.getVarbitValue(id);
                    c.setVarbit(id, Integer.parseInt(a[2]));
                    net.runelite.api.VarbitComposition d = c.getVarbit(id);
                    Log.i(TAG, "set varbit " + id + " " + before + " -> " + c.getVarbitValue(id)
                        + (d != null ? " (varp " + d.getIndex() + " bits " + d.getLeastSignificantBit() + ".." + d.getMostSignificantBit() + ")" : ""));
                    break;
                }
                case "varp": {
                    int[] v = c.getVarps();
                    int before = v[id];
                    v[id] = Integer.parseInt(a[2]);
                    c.queueChangedVarp(id);
                    Log.i(TAG, "set varp " + id + " " + before + " -> " + c.getVarpValue(id));
                    break;
                }
                case "varc": {
                    int before = c.getVarcIntValue(id);
                    c.setVarcIntValue(id, Integer.parseInt(a[2]));
                    Log.i(TAG, "set varc " + id + " " + before + " -> " + c.getVarcIntValue(id));
                    break;
                }
                case "varcstr": {
                    String before = c.getVarcStrValue(id);
                    c.setVarcStrValue(id, a[2]);
                    Log.i(TAG, "set varcstr " + id + " '" + before + "' -> '" + c.getVarcStrValue(id) + "'");
                    break;
                }
                default:
                    Log.w(TAG, "set: unknown kind " + a[0]);
            }
        } catch (Throwable t) {
            Log.w(TAG, "set " + String.join(" ", a) + " failed: " + t);
        }
    }

    static void init(List<Plugin> running) {
        plugins = running;
    }

    static void onGameTick() {
        if ("1".equals(Native.property("debug.osrsrl.camlog"))) logCamera();
        if ("1".equals(Native.property("debug.osrsrl.selflog")) && (tick % 5) == 0) logSelf();
        String say = Native.property("debug.osrsrl.say");  // test hook: Client.addChatMessage (GAMEMESSAGE), once per value
        if (!say.isEmpty() && !say.equals(lastSay) && Host.client() != null) {
            lastSay = say;
            Host.client().addChatMessage(net.runelite.api.ChatMessageType.GAMEMESSAGE, "", say, null);
        }
        String set = Native.property("debug.osrsrl.set");  // test hook: "varbit|varp|varc|varcstr <id> <value>", "whide|wtext <group:child> <v>", "sound <id> <vol>"; once per value
        if (!set.isEmpty() && !set.equals(lastSet) && Host.client() != null) {
            lastSet = set;
            testSetter(Host.client(), set.split(" ", 3));
        }
        String wdump = Native.property("debug.osrsrl.wdump");  // test hook: dump a widget group's getters, once per value
        if (!wdump.isEmpty() && !wdump.equals(lastWdump) && Host.client() != null) {
            lastWdump = wdump;
            dumpGroup(Host.client(), wdump.trim());
        }
        String where = Native.property("debug.osrsrl.where");
        if (!where.isEmpty() && (tick % 5) == 0) logWhere(where);
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

    /**
     * Debug hook: `setprop debug.osrsrl.where <loc id>` logs every scene object with that id: world
     * position, plane and the centre of its click box in UI-canvas and screen pixels (for adb taps).
     */
    /** Debug hook: `setprop debug.osrsrl.selflog 1` logs the local player's and first NPC's API state. */
    private static void logSelf() {
        net.runelite.api.Client c = Host.client();
        if (c == null) return;
        net.runelite.api.Player me = c.getLocalPlayer();
        if (me != null)
            Log.i(TAG, "self " + me.getName() + " weight " + c.getWeight() + " energy " + c.getEnergy() + " cb " + me.getCombatLevel() + " skull " + me.getSkullIcon() + " prayer " + me.getOverheadIcon()
                    + " team " + me.getTeam() + " pose " + me.getPoseAnimation() + " idle/walk/run " + me.getIdlePoseAnimation() + "/"
                    + me.getWalkAnimation() + "/" + me.getRunAnimation() + " " + me.getPlayerComposition());
        for (net.runelite.api.NPC n : c.getTopLevelWorldView().npcs()) {
            net.runelite.api.NPCComposition comp = n.getComposition();
            Log.i(TAG, "npc " + n.getName() + " id " + n.getId() + " pose " + n.getPoseAnimation() + " idle/walk " + n.getIdlePoseAnimation()
                    + "/" + n.getWalkAnimation() + " configs " + (comp == null ? null : java.util.Arrays.toString(comp.getConfigs())));
            break;
        }
    }

    private static void logWhere(String arg) {
        // "deco" / "ground": the decorative or ground objects within 5 tiles of the player instead.
        final String kind = arg.trim();
        int id = -1;
        if (!kind.equals("deco") && !kind.equals("ground"))
            try { id = Integer.parseInt(kind); } catch (NumberFormatException e) { return; }
        net.runelite.api.Client c = Host.client();
        net.runelite.api.coords.WorldPoint me = c != null && c.getLocalPlayer() != null ? c.getLocalPlayer().getWorldLocation() : null;
        AndroidWorldView wv = Host.worldView();
        if (c == null || wv == null) return;
        net.runelite.api.Tile[][][] tiles = wv.getScene().getTiles();
        double toScreen = 1080.0 / Math.max(1, c.getCanvasHeight());
        int found = 0;
        for (int p = 0; p < tiles.length; p++)
            for (net.runelite.api.Tile[] col : tiles[p])
                for (net.runelite.api.Tile t : col) {
                    if (t == null) continue;
                    List<net.runelite.api.TileObject> objs = new java.util.ArrayList<>();
                    if (t.getWallObject() != null) objs.add(t.getWallObject());
                    if (t.getDecorativeObject() != null) objs.add(t.getDecorativeObject());
                    if (t.getGroundObject() != null) objs.add(t.getGroundObject());
                    if (t.getGameObjects() != null) for (net.runelite.api.GameObject g : t.getGameObjects()) if (g != null) objs.add(g);
                    for (net.runelite.api.TileObject o : objs) {
                        if (!o.getWorldLocation().equals(t.getWorldLocation())) continue;
                        if (id >= 0 ? o.getId() != id
                                : !(kind.equals("deco") ? o instanceof net.runelite.api.DecorativeObject : o instanceof net.runelite.api.GroundObject)
                                  || me == null || me.distanceTo(o.getWorldLocation()) > 5 || found >= 8) continue;
                        java.awt.Shape box = o.getClickbox();
                        String at = "offscreen";
                        if (box != null) {
                            java.awt.geom.Rectangle2D b = box.getBounds2D();
                            at = String.format("canvas %d,%d screen %d,%d", (int) b.getCenterX(), (int) b.getCenterY(),
                                    (int) (b.getCenterX() * toScreen), (int) (b.getCenterY() * toScreen));
                        }
                        String model = "no model";
                        if (o instanceof AndroidTileObject) {
                            AndroidTileObject a = (AndroidTileObject) o;
                            int[] m = Native.objectModel(a.plane, a.sceneX(), a.sceneY(), a.layer(), a.tag);
                            if (m != null) model = "model at " + m[0] + "," + m[1] + " h " + m[2] + " orient " + m[3]
                                    + " height " + m[4] + " verts " + (m.length - 5) / 3 + " (local " + o.getX() + "," + o.getY() + " z " + o.getZ() + ")";
                        }
                        Log.i(TAG, "where " + (id >= 0 ? "" + id : kind + " " + o.getId()) + " " + o.getClass().getSimpleName() + " at " + o.getWorldLocation() + ": " + at + "; " + model);
                        found++;
                    }
                }
        if (found == 0) Log.i(TAG, "where " + kind + ": not in scene");
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
            Log.i(TAG, "viewport " + java.util.Arrays.toString(Native.viewport()) + " scale " + Host.client().getScale()
                    + " viewport size " + Host.client().getViewportWidth() + "x" + Host.client().getViewportHeight());
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
