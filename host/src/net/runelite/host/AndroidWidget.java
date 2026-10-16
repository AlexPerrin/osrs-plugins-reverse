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
    @Override public int getType() { return 0; }
    @Override public String getText() { String t = Native.widgetText(id); return t != null ? t : ""; }
    @Override public boolean isSelfHidden() { return Native.widgetHidden(id); }

    /** CC_CREATE child index for {@link Native#widgetItem}; -1 for a component. */
    int childIndex() { return -1; }

    /** Item set by IF_SETOBJECT / CC_SETOBJECT (inventory slots, bank items): -1 if none. */
    @Override public int getItemId() { int[] it = Native.widgetItem(id, childIndex()); return it != null ? it[0] : -1; }
    /** Text colour (IF_SETCOLOUR), written through to the game widget. */
    @Override public int getTextColor() { return Native.widgetColor(id, childIndex()); }
    @Override public Widget setTextColor(int rgb) { Native.widgetSetColor(id, childIndex(), rgb); return this; }
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

    // Children created by plugins live only on the Java side until widget writes exist.
    private final List<Widget> created = new ArrayList<>();

    @Override
    public Widget createChild(int index, int type) {
        DetachedWidget w = new DetachedWidget(this, type);
        if (index >= 0 && index < created.size()) created.set(index, w); else created.add(w);
        return w;
    }

    @Override public Widget createChild(int type) { return createChild(-1, type); }
    @Override public void deleteAllChildren() { created.clear(); }
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
        return d.length == 0 ? null : d;
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
    @Override public void revalidate() {}

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
