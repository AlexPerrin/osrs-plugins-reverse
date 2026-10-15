package net.runelite.host;

import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.List;
import net.runelite.api.Point;
import net.runelite.api.widgets.Widget;

/**
 * A widget by component id; every getter reads the game's current state. Backed so far:
 * text, visibility, geometry (relative position, size, parent) and static children.
 */
public class AndroidWidget extends WidgetBag {
    private final int id;

    AndroidWidget(int id) {
        this.id = id;
    }

    private int[] geo() {
        int[] g = Native.widgetGeometry(id);
        return g != null ? g : new int[] {0, 0, 0, 0, -1};
    }

    @Override public int getId() { return id; }
    @Override public int getType() { return 0; }
    @Override public String getText() { String t = Native.widgetText(id); return t != null ? t : ""; }
    @Override public boolean isSelfHidden() { return Native.widgetHidden(id); }

    @Override
    public boolean isHidden() {
        // Hidden if it or any ancestor is hidden, as in RuneLite.
        int cur = id;
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

    /** Position in game UI pixels, mapped onto the overlay canvas (Host.uiScale). */
    @Override
    public Point getCanvasLocation() {
        int x = 0, y = 0, cur = id;
        for (int depth = 0; cur != -1 && depth < 64; depth++) {
            int[] g = Native.widgetGeometry(cur);
            if (g == null) break;
            x += g[0];
            y += g[1];
            cur = g[4];
        }
        float s = Host.uiScale();
        return new Point(Math.round(x / s), Math.round(y / s));
    }

    @Override
    public Rectangle getBounds() {
        Point p = getCanvasLocation();
        float s = Host.uiScale();
        return new Rectangle(p.getX(), p.getY(), Math.round(getWidth() / s), Math.round(getHeight() / s));
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
    @Override public Widget[] getDynamicChildren() { return created.toArray(new Widget[0]); }
    @Override public Widget[] getChildren() { return created.isEmpty() ? null : created.toArray(new Widget[0]); }
    @Override public Widget getChild(int index) { return index >= 0 && index < created.size() ? created.get(index) : null; }
    @Override public Widget[] getNestedChildren() { return new Widget[0]; }
    @Override public void revalidate() {}

    @Override
    public boolean equals(Object o) {
        return o instanceof AndroidWidget && ((AndroidWidget) o).id == id;
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
