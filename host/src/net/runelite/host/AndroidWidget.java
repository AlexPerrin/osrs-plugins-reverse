package net.runelite.host;

/**
 * A widget by component id; every getter reads the game's current state. Only text and
 * visibility are backed by the game so far.
 */
public class AndroidWidget extends WidgetStub {
    private final int id;

    AndroidWidget(int id) {
        this.id = id;
    }

    @Override public int getId() { return id; }
    @Override public String getText() { String t = Native.widgetText(id); return t != null ? t : ""; }
    @Override public boolean isSelfHidden() { return Native.widgetHidden(id); }
    @Override public boolean isHidden() { return Native.widgetHidden(id); }

    @Override
    public String toString() {
        return "Widget[" + (id >>> 16) + ":" + (id & 0xffff) + " '" + getText() + "']";
    }
}
