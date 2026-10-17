package net.runelite.host;

import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.List;
import net.runelite.api.Point;
import net.runelite.api.widgets.Widget;

/**
 * A widget by component id; every getter reads the game's current state. Backed so far:
 * text, visibility, geometry (relative position, size, parent), item id/quantity, static and
 * CC_CREATE children.
 */
public class AndroidWidget extends WidgetBag {
    final int id;

    AndroidWidget(int id) {
        this.id = id;
    }

    /** Raw geometry {x, y, width, height, parentId} or null. */
    int[] rawGeo() { return Native.widgetGeometry(id); }

    private int[] geo() {
        int[] g = rawGeo();
        return g != null ? g : new int[] {0, 0, 0, 0, -1};
    }

    @Override public int getId() { return id; }
    @Override public int getType() { return prop(34, 0); }
    @Override public String getText() { String t = Native.widgetText(id); return t != null ? t : ""; }
    @Override public boolean isSelfHidden() { return Native.widgetHidden(id); }

    /** CC_CREATE child index for {@link Native#widgetItem}; -1 for a component. */
    int childIndex() { return -1; }

    /** Item set by IF_SETOBJECT / CC_SETOBJECT (inventory slots, bank items): -1 if none. */
    @Override public int getItemId() { int[] it = Native.widgetItem(id, childIndex()); return it != null ? it[0] : -1; }
    /** Text colour (IF_SETCOLOUR), written through to the game widget. */
    @Override public int getTextColor() { return Native.widgetColor(id, childIndex()); }
    @Override public Widget setHidden(boolean hidden) { Native.widgetSetHidden(id, childIndex(), hidden); return this; }
    @Override public Widget setText(String text) { Native.widgetSetText(id, childIndex(), text); return this; }
    @Override public Widget setTextColor(int rgb) { Native.widgetSetColor(id, childIndex(), rgb); return this; }
    // Field setters written into the game widget (CC_SET* offsets, docs/offsets.md); revalidate()
    // re-runs the game's layout as CC_SETPOSITION/CC_SETSIZE do.
    private Widget set(int offset, int value) { Native.widgetSetField(id, childIndex(), offset, value); return this; }
    @Override public Widget setXPositionMode(int v) { return set(0x3c, v); }
    @Override public Widget setYPositionMode(int v) { return set(0x40, v); }
    @Override public Widget setWidthMode(int v) { return set(0x44, v); }
    @Override public Widget setHeightMode(int v) { return set(0x48, v); }
    @Override public Widget setOriginalX(int v) { return set(0x4c, v); }
    @Override public Widget setOriginalY(int v) { return set(0x50, v); }
    @Override public Widget setOriginalWidth(int v) { return set(0x54, v); }
    @Override public Widget setOriginalHeight(int v) { return set(0x58, v); }
    @Override public Widget setFilled(boolean v) { return set(0xa8, v ? 1 : 0); }
    @Override public Widget setOpacity(int v) { return set(0xb0, v); }
    @Override public Widget setSpriteId(int v) { return set(0xc8, v); }
    @Override public Widget setSpriteTiling(boolean v) { return set(0xec, v ? 1 : 0); }
    @Override public void setBorderType(int v) { set(0xf0, v); }
    @Override public void setFlippedVertically(boolean v) { set(0xf8, v ? 1 : 0); }
    @Override public void setFlippedHorizontally(boolean v) { set(0xf9, v ? 1 : 0); }
    @Override public Widget setFontId(int v) { return set(0x150, v); }
    @Override public Widget setXTextAlignment(int v) { return set(0x18c, v); }
    @Override public Widget setYTextAlignment(int v) { return set(0x190, v); }
    @Override public Widget setTextShadowed(boolean v) { return set(0x194, v ? 1 : 0); }
    @Override public void setNoClickThrough(boolean v) { set(0xb98, v ? 1 : 0); }
    @Override public void setNoScrollThrough(boolean v) { set(0xb99, v ? 1 : 0); }
    @Override public void revalidate() { Native.widgetRevalidate(id, childIndex()); }
    @Override public int getIndex() { return childIndex(); }
    // Script-visible properties read from the game widget (Native.widgetProps).
    private int prop(int i, int dflt) { int[] p = Native.widgetProps(id, childIndex()); return p != null ? p[i] : dflt; }
    @Override public int getScrollX() { return prop(0, 0); }
    @Override public int getScrollY() { return prop(1, 0); }
    @Override public int getScrollWidth() { return prop(2, 0); }
    @Override public int getScrollHeight() { return prop(3, 0); }
    @Override public int getModelZoom() { return prop(4, 0); }
    @Override public int getRotationX() { return prop(5, 0); }
    @Override public int getRotationY() { return prop(6, 0); }
    @Override public int getRotationZ() { return prop(7, 0); }
    @Override public int getOpacity() { return prop(8, 0); }
    @Override public int getModelType() { return prop(9, 0); }
    @Override public int getModelId() { return prop(10, -1); }
    @Override public int getAnimationId() { return prop(11, -1); }
    private String[] strings() { return Native.widgetStrings(id, childIndex()); }
    @Override public String getName() { String[] s = strings(); return s != null && s[0] != null ? s[0] : ""; }
    @Override public String getTargetVerb() { String[] s = strings(); return s != null && s[1] != null ? s[1] : ""; }
    // Name/verb/ops written into the game widget (CC_SETOPBASE/CC_SETTARGETVERB/CC_SETOP), so the
    // game's menu lists them; a Java onOp listener is run by WidgetListeners when one is selected.
    @Override public Widget setName(String name) { Native.widgetSetString(id, childIndex(), -1, name != null ? name : ""); return this; }
    @Override public void setTargetVerb(String verb) { Native.widgetSetString(id, childIndex(), -2, verb != null ? verb : ""); }
    @Override public void setAction(int index, String action) {
        if (index < 0 || index > 31) return;
        Native.widgetSetString(id, childIndex(), index, action != null ? action : "");
        WidgetListeners.syncFlags(id, childIndex());
    }
    @Override public void clearActions() {
        String[] a = getActions();
        for (int i = 0; a != null && i < a.length; i++) if (a[i] != null) Native.widgetSetString(id, childIndex(), i, "");
        WidgetListeners.syncFlags(id, childIndex());
    }
    @Override public void setOnOpListener(Object... args) { WidgetListeners.setOnOp(id, childIndex(), args); }
    @Override public Object[] getOnOpListener() { return WidgetListeners.onOp(id, childIndex()); }
    @Override public Widget setHasListener(boolean v) { return this; }
    @Override public String[] getActions() {
        String[] s = strings();
        return s == null || s.length <= 2 ? null : java.util.Arrays.copyOfRange(s, 2, s.length);
    }
    @Override public int getSpriteId() { return prop(12, -1); }
    @Override public boolean getSpriteTiling() { return prop(13, 0) != 0; }
    @Override public int getBorderType() { return prop(14, 0); }
    @Override public boolean isFlippedVertically() { return prop(16, 0) != 0; }
    @Override public boolean isFlippedHorizontally() { return prop(17, 0) != 0; }
    @Override public boolean isFilled() { return prop(18, 0) != 0; }
    @Override public boolean getTextShadowed() { return prop(19, 0) != 0; }
    @Override public int getXTextAlignment() { return prop(20, 0); }
    @Override public int getYTextAlignment() { return prop(21, 0); }
    @Override public int getLineHeight() { return prop(22, 0); }
    @Override public int getFontId() { return prop(23, -1); }
    @Override public int getOriginalX() { return prop(24, 0); }
    @Override public int getOriginalY() { return prop(25, 0); }
    @Override public int getXPositionMode() { return prop(26, 0); }
    @Override public int getYPositionMode() { return prop(27, 0); }
    @Override public int getOriginalWidth() { return prop(28, 0); }
    @Override public int getOriginalHeight() { return prop(29, 0); }
    @Override public int getWidthMode() { return prop(30, 0); }
    @Override public int getHeightMode() { return prop(31, 0); }
    @Override public boolean getNoClickThrough() { return prop(32, 0) != 0; }
    @Override public boolean getNoScrollThrough() { return prop(33, 0) != 0; }
    @Override public int getItemQuantity() { int[] it = Native.widgetItem(id, childIndex()); return it != null && it[0] != -1 ? it[1] : 0; }

    @Override
    public boolean isHidden() {
        return isSelfHidden() || ancestorsHidden(getParentId());
    }

    /** Hidden if it or any ancestor is hidden, as in RuneLite. */
    static boolean ancestorsHidden(int start) {
        int cur = start;
        for (int depth = 0; cur != -1 && depth < 64; depth++) {
            if (Native.widgetHidden(cur)) return true;
            int[] g = Native.widgetGeometry(cur);
            cur = g != null ? g[4] : -1;
        }
        return false;
    }

    @Override public int getRelativeX() { return geo()[0]; }
    @Override public int getRelativeY() { return geo()[1]; }
    @Override public int getWidth() { return geo()[2]; }
    @Override public int getHeight() { return geo()[3]; }
    @Override public int getParentId() { return geo()[4]; }

    @Override
    public Widget getParent() {
        int p = getParentId();
        return p == -1 ? null : new AndroidWidget(p);
    }

    /** Position on the game's UI canvas, which is also the overlay canvas. */
    @Override
    public Point getCanvasLocation() {
        int[] own = rawGeo();
        if (own == null) return new Point(0, 0);
        int x = own[0], y = own[1], cur = own[4];
        for (int depth = 0; cur != -1 && depth < 64; depth++) {
            int[] g = Native.widgetGeometry(cur);
            if (g == null) break;
            x += g[0];
            y += g[1];
            cur = g[4];
        }
        return new Point(x, y);
    }

    @Override
    public Rectangle getBounds() {
        Point p = getCanvasLocation();
        return new Rectangle(p.getX(), p.getY(), getWidth(), getHeight());
    }

    @Override
    public boolean contains(Point point) {
        return getBounds().contains(point.getX(), point.getY());
    }

    /** Components of the same group whose parent is this widget. */
    @Override
    public Widget[] getStaticChildren() {
        int group = id >>> 16, n = Native.widgetChildCount(group);
        List<Widget> out = new ArrayList<>();
        for (int child = 0; child < n; child++) {
            int cid = group << 16 | child;
            int[] g = Native.widgetGeometry(cid);
            if (g != null && g[4] == id) out.add(new AndroidWidget(cid));
        }
        return out.toArray(new Widget[0]);
    }

    // Children created by plugins: in the game's tree through CC_CREATE (static layer parents, on
    // the game thread), else Java-side only (DetachedWidget, not drawn).
    private final List<Widget> created = new ArrayList<>();
    /**
     * Plugin children are created in the game's tree (CC_CREATE); `setprop debug.osrsrl.createchild 0`
     * keeps them Java-side instead. On by default since 2026-10-16 (Skills Tab Progress Bars' bars
     * draw and update, no duplicates after reopening the tab, clean login).
     */
    static final boolean CREATE_IN_GAME = !"0".equals(Native.property("debug.osrsrl.createchild"));

    @Override
    public Widget createChild(int index, int type) {
        if (CREATE_IN_GAME && childIndex() < 0 && type != 12 && Native.isClientThread()) {
            int at = Native.widgetCreateChild(id, index, type);
            if (at >= 0) { WidgetListeners.forget(id, at); return new Dynamic(id, at); }
        }
        DetachedWidget w = new DetachedWidget(this, type);
        if (index >= 0 && index < created.size()) created.set(index, w); else created.add(w);
        return w;
    }

    @Override public Widget createChild(int type) { return createChild(-1, type); }
    @Override public void deleteAllChildren() {
        created.clear();
        if (CREATE_IN_GAME && childIndex() < 0 && Native.isClientThread()) {
            Native.widgetDeleteChildren(id);
            WidgetListeners.forget(id, -1);
        }
    }
    /** The game's CC_CREATE children (null slots stay null), then plugin-created ones. */
    @Override public Widget[] getDynamicChildren() {
        Widget[] game = gameChildren();
        if (created.isEmpty()) return game;
        List<Widget> all = new ArrayList<>(java.util.Arrays.asList(game));
        all.addAll(created);
        return all.toArray(new Widget[0]);
    }
    @Override public Widget[] getChildren() {
        Widget[] d = getDynamicChildren();
        if (d.length == 0) return null;
        synchronized (issued) { if (issued.size() < 4096) issued.add(new Widget[][] {d, d.clone()}); }
        return d;
    }

    // On desktop getChildren() is the game's own array: a plugin that nulls a slot (XP Drop's
    // hideSkillIcons) removes that child. Here the array is a copy, so the arrays handed out are
    // remembered and, after each event dispatch (applyChildEdits), a game child whose slot was
    // nulled is hidden in the game instead.
    private static final List<Widget[][]> issued = new ArrayList<>();

    static void applyChildEdits() {
        List<Widget[][]> list;
        synchronized (issued) {
            if (issued.isEmpty()) return;
            list = new ArrayList<>(issued);
            issued.clear();
        }
        for (Widget[][] pair : list) {
            Widget[] now = pair[0], was = pair[1];
            for (int i = 0; i < now.length; i++)
                if (now[i] == null && was[i] instanceof Dynamic) {
                    was[i].setHidden(true);
                    synchronized (suppressed) { suppressed.put((Dynamic) was[i], Host.cycleStamp + SUPPRESS_CYCLES); }
                }
        }
    }

    // The game's scripts can unhide a child after the event (XP drops are shown by the script that
    // ScriptPreFired precedes), so a nulled child is re-hidden every cycle for a while.
    private static final int SUPPRESS_CYCLES = 150;
    private static int rehideLogs;
    private static final java.util.Map<Dynamic, Integer> suppressed = new java.util.HashMap<>();

    /** Game thread, once per cycle. */
    static void reapplyHidden() {
        synchronized (suppressed) {
            if (suppressed.isEmpty()) return;
            for (java.util.Iterator<java.util.Map.Entry<Dynamic, Integer>> it = suppressed.entrySet().iterator(); it.hasNext(); ) {
                java.util.Map.Entry<Dynamic, Integer> e = it.next();
                if (e.getValue() - Host.cycleStamp < 0) { it.remove(); continue; }
                if (!e.getKey().isSelfHidden()) {
                    e.getKey().setHidden(true);
                    if (rehideLogs++ < 5) android.util.Log.i(Host.TAG, "re-hid nulled child " + e.getKey().id + "[" + e.getKey().index + "]");
                }
            }
        }
    }
    @Override public Widget getChild(int index) {
        Widget[] d = getDynamicChildren();
        return index >= 0 && index < d.length ? d[index] : null;
    }

    Widget[] gameChildren() {
        int n = Native.widgetDynamicCount(id);
        Widget[] out = new Widget[n];
        for (int i = 0; i < n; i++)
            if (Native.widgetDynamicGeometry(id, i) != null) out[i] = new Dynamic(id, i);
        return out;
    }

    /** A child created by the game's CC_CREATE under component `parent`. */
    static final class Dynamic extends AndroidWidget {
        final int index;

        Dynamic(int parent, int index) {
            super(parent);
            this.index = index;
        }

        @Override int[] rawGeo() { return Native.widgetDynamicGeometry(id, index); }
        @Override public int getIndex() { return index; }
        @Override int childIndex() { return index; }
        @Override public String getText() { String t = Native.widgetDynamicText(id, index); return t != null ? t : ""; }
        @Override public boolean isSelfHidden() { return Native.widgetDynamicHidden(id, index); }
        @Override public boolean isHidden() { return isSelfHidden() || ancestorsHidden(id); }
        @Override public Widget getParent() { return new AndroidWidget(id); }
        @Override public Widget[] getStaticChildren() { return new Widget[0]; }
        @Override Widget[] gameChildren() { return new Widget[0]; }
        @Override public boolean equals(Object o) { return o instanceof Dynamic && ((Dynamic) o).id == id && ((Dynamic) o).index == index; }
        @Override public int hashCode() { return id * 31 + index; }
    }
    @Override public Widget[] getNestedChildren() { return new Widget[0]; }

    @Override
    public boolean equals(Object o) {
        return o instanceof AndroidWidget && !(o instanceof Dynamic) && ((AndroidWidget) o).id == id;
    }

    @Override
    public int hashCode() {
        return id;
    }

    @Override
    public String toString() {
        int[] g = geo();
        return "Widget[" + (id >>> 16) + ":" + (id & 0xffff) + " '" + getText() + "' " + g[2] + "x" + g[3] + " parent " + g[4] + "]";
    }
}
