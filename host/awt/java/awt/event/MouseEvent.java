package java.awt.event;

import java.awt.Point;

/** Android shim of java.awt.event.MouseEvent (subset). */
public class MouseEvent extends java.util.EventObject {
    public static final int MOUSE_CLICKED = 500, MOUSE_PRESSED = 501, MOUSE_RELEASED = 502, MOUSE_MOVED = 503,
            MOUSE_ENTERED = 504, MOUSE_EXITED = 505, MOUSE_DRAGGED = 506, MOUSE_WHEEL = 507;
    public static final int NOBUTTON = 0, BUTTON1 = 1, BUTTON2 = 2, BUTTON3 = 3;
    private final int id, x, y, button, clickCount, modifiers;
    private boolean consumed;

    public MouseEvent(Object source, int id, long when, int modifiers, int x, int y, int clickCount, boolean popup, int button) {
        super(source);
        this.id = id; this.modifiers = modifiers; this.x = x; this.y = y; this.clickCount = clickCount; this.button = button;
    }
    public int getID() { return id; }
    public int getX() { return x; }
    public int getY() { return y; }
    public Point getPoint() { return new Point(x, y); }
    public int getButton() { return button; }
    public int getClickCount() { return clickCount; }
    public int getModifiersEx() { return modifiers; }
    public void consume() { consumed = true; }
    public boolean isConsumed() { return consumed; }
}
