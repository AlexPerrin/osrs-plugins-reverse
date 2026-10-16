package net.runelite.host;

import android.util.Log;
import java.util.ArrayList;
import java.util.List;
import net.runelite.api.MenuAction;
import net.runelite.api.MenuEntry;
import net.runelite.api.Perspective;
import net.runelite.api.coords.LocalPoint;

/**
 * Client.menuAction and the desktop "menu under the mouse" for plugins that drive the client
 * (OpenRune-Developer-Tools). Android has no hover and no direct menu-action entry point, so both go
 * through the game's own menu, as a long-press does: the scene is ray-picked at a point on the next
 * rendered frame (Native.pickAt, the touch-down pick), then the long-press menu is built there
 * without being shown (Native.menuBuildAt) and an entry runs through the game's select, exactly as
 * picking that row. So a menuAction completes a frame or two later (on desktop the action is also
 * only sent with the next tick). The point is the virtual mouse (DevInput) when fresh, else points
 * across the target (NPC/player hull, object, tile, widget), tried in turn until the entry is there.
 */
final class DevMenu {
    private DevMenu() {}

    private static final String TAG = Host.TAG;
    private static final int PENDING_CYCLES = 90;  // ~3 s at 30 fps

    // --- picking ---
    private static java.awt.Point picked;  // canvas point of the last pickAt

    private static void pick(java.awt.Point canvas) {
        int[] screen = Native.screenSize();
        java.awt.Dimension c = OverlayRenderer.canvasSize();
        if (screen == null || c.width <= 0 || c.height <= 0) return;
        Native.pickAt(Math.round(canvas.x * (float) screen[0] / c.width), Math.round(canvas.y * (float) screen[1] / c.height));
        picked = new java.awt.Point(canvas);
    }

    private static boolean pickedAt(java.awt.Point p) { return p.equals(picked) && Native.pickReady(); }

    /** Builds the menu at p (picked already), returns its entries top first, and discards it. */
    private static AndroidMenuEntry[] buildAndRead(java.awt.Point p) {
        if (Native.menuOpen() || !Native.menuBuildAt(p.x, p.y)) return null;
        try {
            return AndroidMenuEntry.fromNative(Native.menuEntries());
        } finally {
            Native.menuDiscard();
        }
    }

    // --- hover menu ---
    private static int hoverStamp = -1;
    private static java.awt.Point hoverPoint;
    private static AndroidMenuEntry[] hover = new AndroidMenuEntry[0];

    /**
     * Game thread, no menu open: the entries the game builds at the virtual mouse (top first), empty
     * until the point has been picked (a frame or two after the mouse moved), or null when there is no
     * fresh virtual mouse. Built once per point.
     */
    static AndroidMenuEntry[] hoverMenu() {
        java.awt.Point m = DevInput.virtualMouse();
        if (m == null || !Native.isClientThread()) return null;
        if (m.equals(hoverPoint) && hoverStamp >= 0) return hover;
        if (!m.equals(picked)) {
            pick(m);
            return new AndroidMenuEntry[0];
        }
        if (!Native.pickReady()) return new AndroidMenuEntry[0];
        AndroidMenuEntry[] es = buildAndRead(m);
        hover = es != null ? es : new AndroidMenuEntry[0];
        hoverPoint = m;
        hoverStamp = Host.cycleStamp;
        return hover;
    }

    /** Game thread, each client cycle (Host CLIENT_TICK). */
    static void onCycle() {
        java.awt.Point m = DevInput.virtualMouse();
        if (m != null && !m.equals(picked) && pending == null && Native.isClientThread()) pick(m);  // pick ahead of getMenuEntries
        if (m == null || !m.equals(hoverPoint)) hoverStamp = -1;
        if (pending != null) pending.step();
    }

    // --- menuAction ---
    private static Pending pending;

    private static final class Pending {
        final int p0, p1, id;
        final MenuAction type;
        final String option;
        final List<java.awt.Point> points;
        int index, cycles;

        Pending(int p0, int p1, MenuAction type, int id, String option, List<java.awt.Point> points) {
            this.p0 = p0;
            this.p1 = p1;
            this.type = type;
            this.id = id;
            this.option = option;
            this.points = points;
            pick(points.get(0));
        }

        void step() {
            if (++cycles > PENDING_CYCLES) {
                Log.w(TAG, "menuAction " + type + " id " + id + ": timed out");
                pending = null;
                return;
            }
            java.awt.Point p = points.get(index);
            if (!pickedAt(p)) return;
            AndroidMenuEntry[] es = buildAndRead(p);
            AndroidMenuEntry pick = es == null ? null : find(es, p0, p1, type, id, option);
            if (pick != null) {
                pending = null;
                runAt(p, pick);
                return;
            }
            if (++index >= points.size()) {
                Log.w(TAG, "menuAction: no entry " + type + " id " + id + " '" + option + "' at " + points.size() + " points; last:" + describe(es));
                pending = null;
                return;
            }
            DevMenu.pick(points.get(index));
        }
    }

    /** Builds at p again (picked), selects the entry like `pick`, discards. */
    private static void runAt(java.awt.Point p, AndroidMenuEntry want) {
        if (!Native.menuBuildAt(p.x, p.y)) return;
        try {
            AndroidMenuEntry[] es = AndroidMenuEntry.fromNative(Native.menuEntries());
            AndroidMenuEntry e = find(es, want.getParam0(), want.getParam1(), want.getType(), want.getIdentifier(), want.getOption());
            if (e == null) return;
            Log.i(TAG, "menuAction -> " + e);
            Native.menuSelect(e.nativeIndex);
        } finally {
            Native.menuDiscard();
        }
    }

    private static String describe(MenuEntry[] es) {
        StringBuilder sb = new StringBuilder();
        if (es != null) for (MenuEntry e : es) sb.append(" [").append(plain(e.getOption())).append(' ').append(plain(e.getTarget())).append(' ').append(e.getType()).append(']');
        return sb.toString();
    }

    /** Client.menuAction. Game thread. */
    static void menuAction(int p0, int p1, MenuAction type, int id, int itemId, String option, String target) {
        if (!Native.isClientThread()) {
            Log.w(TAG, "menuAction off the game thread ignored: " + type);
            return;
        }
        if (!DevInput.inputAllowed()) return;
        // Widget continue and default (op 1) actions are what a tap on the widget does.
        if (type == MenuAction.WIDGET_CONTINUE || (type == MenuAction.CC_OP && id == 1 && DevInput.virtualMouse() == null)) {
            java.awt.Point c = widgetPoint(p0, p1);
            if (c == null) {
                Log.w(TAG, "menuAction: widget " + (p1 >>> 16) + ":" + (p1 & 0xffff) + "[" + p0 + "] not visible");
                return;
            }
            Log.i(TAG, "menuAction " + type + " -> tap " + c);
            DevInput.tap(c.x, c.y);
            return;
        }
        if (Native.menuOpen()) {  // an open menu: pick the row
            AndroidMenuEntry e = find(AndroidMenuEntry.fromNative(Native.menuEntries()), p0, p1, type, id, option);
            if (e != null) Native.menuSelect(e.nativeIndex);
            else Log.w(TAG, "menuAction: no entry " + type + " in the open menu");
            Native.menuClose();
            return;
        }
        java.awt.Point m = DevInput.virtualMouse();
        if (m != null && pickedAt(m)) {  // hover flow: the menu under the mouse
            AndroidMenuEntry[] es = buildAndRead(m);
            AndroidMenuEntry e = es == null ? null : find(es, p0, p1, type, id, option);
            if (e != null) {
                runAt(m, e);
                return;
            }
        }
        List<java.awt.Point> points = new ArrayList<>();
        if (m != null) points.add(m);
        points.addAll(targetPoints(p0, p1, type, id));
        if (points.isEmpty()) {
            Log.w(TAG, "menuAction: " + type + " id " + id + " (" + p0 + ", " + p1 + ") is not on screen");
            return;
        }
        if (pending != null) Log.w(TAG, "menuAction: replacing pending " + pending.type);
        pending = new Pending(p0, p1, type, id, option, points);
    }

    private static String plain(String s) { return s == null ? "" : s.replaceAll("<[^>]*>", "").trim(); }

    private static AndroidMenuEntry find(AndroidMenuEntry[] es, int p0, int p1, MenuAction type, int id, String option) {
        for (AndroidMenuEntry e : es) if (matches(e, p0, p1, type, id, option)) return e;
        if (type == MenuAction.WALK) for (AndroidMenuEntry e : es) if (e.getType() == MenuAction.WALK) return e;
        return null;
    }

    private static boolean matches(AndroidMenuEntry e, int p0, int p1, MenuAction type, int id, String option) {
        if (e.getType() != type) return false;
        if (option != null && !option.isEmpty() && !plain(e.getOption()).equalsIgnoreCase(plain(option))) return false;
        switch (type) {
            case NPC_FIRST_OPTION: case NPC_SECOND_OPTION: case NPC_THIRD_OPTION: case NPC_FOURTH_OPTION:
            case NPC_FIFTH_OPTION: case EXAMINE_NPC: case WIDGET_TARGET_ON_NPC:
            case PLAYER_FIRST_OPTION: case PLAYER_SECOND_OPTION: case PLAYER_THIRD_OPTION: case PLAYER_FOURTH_OPTION:
            case PLAYER_FIFTH_OPTION: case PLAYER_SIXTH_OPTION: case PLAYER_SEVENTH_OPTION: case PLAYER_EIGHTH_OPTION:
                return e.getIdentifier() == id;
            case WALK:
                return e.getParam0() == p0 && e.getParam1() == p1;
            default:
                return e.getIdentifier() == id && e.getParam0() == p0 && e.getParam1() == p1;
        }
    }

    /** Canvas points across the action's target, most likely first; empty if off screen. */
    static List<java.awt.Point> targetPoints(int p0, int p1, MenuAction type, int id) {
        List<java.awt.Point> out = new ArrayList<>();
        net.runelite.api.Client client = Host.client();
        int plane = client.getTopLevelWorldView().getPlane();
        switch (type) {
            case NPC_FIRST_OPTION: case NPC_SECOND_OPTION: case NPC_THIRD_OPTION: case NPC_FOURTH_OPTION:
            case NPC_FIFTH_OPTION: case EXAMINE_NPC: case WIDGET_TARGET_ON_NPC: {
                AndroidNPC n = Host.npc(id);
                if (n != null) hullPoints(n.getConvexHull(), n.getLocalLocation(), plane, n.getLogicalHeight(), out);
                break;
            }
            case PLAYER_FIRST_OPTION: case PLAYER_SECOND_OPTION: case PLAYER_THIRD_OPTION: case PLAYER_FOURTH_OPTION:
            case PLAYER_FIFTH_OPTION: case PLAYER_SIXTH_OPTION: case PLAYER_SEVENTH_OPTION: case PLAYER_EIGHTH_OPTION: {
                AndroidPlayer pl = Host.player(id);
                if (pl != null) hullPoints(pl.getConvexHull(), pl.getLocalLocation(), plane, pl.getLogicalHeight(), out);
                break;
            }
            case GAME_OBJECT_FIRST_OPTION: case GAME_OBJECT_SECOND_OPTION: case GAME_OBJECT_THIRD_OPTION:
            case GAME_OBJECT_FOURTH_OPTION: case GAME_OBJECT_FIFTH_OPTION: case EXAMINE_OBJECT: case WIDGET_TARGET_ON_GAME_OBJECT: {
                net.runelite.api.ObjectComposition oc = client.getObjectDefinition(id);
                int sx = oc != null ? Math.max(1, oc.getSizeX()) : 1, sy = oc != null ? Math.max(1, oc.getSizeY()) : 1;
                LocalPoint lp = LocalPoint.fromScene(p0, p1, client.getTopLevelWorldView());
                LocalPoint c = new LocalPoint(lp.getX() + (sx - 1) * 64, lp.getY() + (sy - 1) * 64, lp.getWorldView());
                for (int z : new int[] {60, 20, 120, 200}) add(out, project(client, c, plane, z));
                break;
            }
            case GROUND_ITEM_FIRST_OPTION: case GROUND_ITEM_SECOND_OPTION: case GROUND_ITEM_THIRD_OPTION:
            case GROUND_ITEM_FOURTH_OPTION: case GROUND_ITEM_FIFTH_OPTION: case EXAMINE_ITEM_GROUND: case WALK:
                add(out, project(client, LocalPoint.fromScene(p0, p1, client.getTopLevelWorldView()), plane, 0));
                break;
            default:
                add(out, widgetPoint(p0, p1));
                break;
        }
        return out;
    }

    private static void add(List<java.awt.Point> out, java.awt.Point p) {
        if (p != null && !out.contains(p)) out.add(p);
    }

    /**
     * Centre and a 3x3 grid over the part of the hull that is on the canvas, points inside the
     * hull and off the game's UI first (an NPC at the left edge is partly under the button
     * column); projected body points if there's no hull.
     */
    private static void hullPoints(java.awt.Shape hull, LocalPoint lp, int plane, int height, List<java.awt.Point> out) {
        java.awt.Rectangle hb = hull != null ? hull.getBounds() : null;
        java.awt.Dimension c = OverlayRenderer.canvasSize();
        java.awt.Rectangle b = hb == null ? null : hb.intersection(new java.awt.Rectangle(0, 0, c.width, c.height));
        if (b != null && b.width > 0 && b.height > 0) {
            List<java.awt.Point> grid = new ArrayList<>();
            grid.add(new java.awt.Point(b.x + b.width / 2, b.y + b.height / 2));
            for (int gy = 1; gy <= 3; gy++)
                for (int gx = 1; gx <= 3; gx++)
                    grid.add(new java.awt.Point(b.x + b.width * gx / 4, b.y + b.height * gy / 4));
            for (int pass = 0; pass < 2; pass++)
                for (java.awt.Point p : grid) {
                    boolean good = hull.contains(p) && !OverlayRenderer.overUi(p.x, p.y);
                    if (good == (pass == 0)) add(out, clip(p));
                }
        }
        int h = height > 0 ? height : 200;
        for (int z : new int[] {h / 2, h / 4, h * 3 / 4}) add(out, project(Host.client(), lp, plane, z));
    }

    private static java.awt.Point clip(java.awt.Point p) {
        java.awt.Dimension c = OverlayRenderer.canvasSize();
        return p.x < 0 || p.y < 0 || p.x >= c.width || p.y >= c.height ? null : p;
    }

    private static java.awt.Point project(net.runelite.api.Client client, LocalPoint lp, int plane, int z) {
        if (lp == null) return null;
        net.runelite.api.Point p = Perspective.localToCanvas(client, lp, plane, z);
        return p == null ? null : clip(new java.awt.Point(p.getX(), p.getY()));
    }

    /** Centre of widget p1 (child p0 when >= 0) on the canvas, or null if hidden or missing. */
    static java.awt.Point widgetPoint(int p0, int p1) {
        net.runelite.api.widgets.Widget w = Host.client().getWidget(p1);
        if (w != null && p0 >= 0) {
            net.runelite.api.widgets.Widget c = w.getChild(p0);
            if (c != null) w = c;
        }
        if (w == null || w.isHidden()) return null;
        java.awt.Rectangle b = w.getBounds();
        if (b == null || b.width <= 0 || b.height <= 0) return null;
        return clip(new java.awt.Point(b.x + b.width / 2, b.y + b.height / 2));
    }
}
