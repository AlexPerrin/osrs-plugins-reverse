package net.runelite.host;

import net.runelite.api.ScriptEvent;
import net.runelite.api.ScriptEventBuilder;
import net.runelite.api.widgets.Widget;

/**
 * Client.createScriptEventBuilder: run() substitutes RuneLite's magic arguments (event_com,
 * event_comsubid, event_op, ...) in Java and runs the script through Client.runScript, so the
 * game's ScriptEvent fields for the source widget are not set (scripts that read the event's
 * widget through those arguments work; the mouse position is 0, 0).
 */
final class AndroidScriptEvent implements ScriptEvent, ScriptEventBuilder {
    private final net.runelite.api.Client client;
    private Object[] arguments;
    private Widget source, target;
    private int op;

    AndroidScriptEvent(net.runelite.api.Client client, Object[] arguments) {
        this.client = client;
        this.arguments = arguments;
    }

    @Override public Object[] getArguments() { return arguments; }
    @Override public ScriptEventBuilder setArguments(Object[] a) { arguments = a; return this; }
    @Override public Widget getSource() { return source; }
    @Override public ScriptEventBuilder setSource(Widget w) { source = w; return this; }
    @Override public Widget getTarget() { return target; }
    @Override public ScriptEventBuilder setTarget(Widget w) { target = w; return this; }
    @Override public int getOp() { return op; }
    @Override public ScriptEventBuilder setOp(int o) { op = o; return this; }
    @Override public ScriptEvent build() { return this; }

    @Override public String getOpbase() { return source != null ? source.getName() : ""; }
    @Override public int getMouseX() { return 0; }
    @Override public int getMouseY() { return 0; }
    @Override public int getTypedKeyCode() { return 0; }
    @Override public int getTypedKeyChar() { return 0; }
    @Override public ScriptEvent setCanSendPackets(boolean b) { return this; }

    @Override public void run() {
        if (arguments == null || arguments.length == 0) return;
        Object[] a = arguments.clone();
        for (int i = 1; i < a.length; i++) {
            if (a[i] instanceof Integer) a[i] = resolve((Integer) a[i]);
            else if (NAME.equals(a[i])) a[i] = getOpbase();
        }
        client.runScript(a);
    }

    private Object resolve(int v) {
        switch (v) {
            case MOUSE_X: case MOUSE_Y: case KEY_CODE: case KEY_CHAR: return 0;
            case MENU_OP: return op;
            case WIDGET_ID: return source != null ? source.getId() : -1;
            case WIDGET_INDEX: return source != null ? source.getIndex() : -1;
            case WIDGET_TARGET_ID: return target != null ? target.getId() : -1;
            case WIDGET_TARGET_INDEX: return target != null ? target.getIndex() : -1;
            default: return v;
        }
    }
}
