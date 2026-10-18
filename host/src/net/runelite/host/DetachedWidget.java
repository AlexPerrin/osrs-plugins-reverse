package net.runelite.host;

import net.runelite.api.widgets.Widget;

/** A widget a plugin created with createChild(): properties are kept here, not in the game. */
public class DetachedWidget extends WidgetBag {
    private final Widget parent;
    private final int type;

    DetachedWidget(Widget parent, int type) {
        this.parent = parent;
        this.type = type;
    }

    @Override public Widget getParent() { return parent; }
    @Override public int getParentId() { return parent.getId(); }
    @Override public int getId() { return parent.getId(); }
    @Override public int getType() { return type; }
    @Override public void revalidate() {}

    private static final Widget SINK = new WidgetBag();

    /**
     * A widget that absorbs everything (WidgetBag: setters kept, createChild returns itself, nothing is
     * drawn). ChatboxPanelManager.getContainerWidget returns it: desktop chatbox inputs that draw into the
     * chatbox container (Wiki's search predictions) then run without a chatbox; their text is not shown.
     */
    public static Widget sink() { return SINK; }
}
