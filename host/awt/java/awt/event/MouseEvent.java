package java.awt.event;

import java.awt.Component;
import java.awt.Point;

/** Android shim of java.awt.event.MouseEvent. */
public class MouseEvent extends InputEvent {
    public static final int MOUSE_CLICKED = 500, MOUSE_PRESSED = 501, MOUSE_RELEASED = 502, MOUSE_MOVED = 503,
            MOUSE_ENTERED = 504, MOUSE_EXITED = 505, MOUSE_DRAGGED = 506, MOUSE_WHEEL = 507;
    public static final int NOBUTTON = 0, BUTTON1 = 1, BUTTON2 = 2, BUTTON3 = 3;
    private int x, y;
    private final int button, clickCount;
    private final boolean popup;

    public MouseEvent(Component source, int id, long when, int modifiers, int x, int y, int clickCount, boolean popup, int button) {
        super(source, id, when, modifiers);
        this.x = x; this.y = y; this.clickCount = clickCount; this.popup = popup; this.button = button;
    }
    public MouseEvent(Component source, int id, long when, int modifiers, int x, int y, int clickCount, boolean popup) {
        this(source, id, when, modifiers, x, y, clickCount, popup, NOBUTTON);
    }
    public MouseEvent(Component source, int id, long when, int modifiers, int x, int y, int xAbs, int yAbs, int clickCount,
                      boolean popup, int button) {
        this(source, id, when, modifiers, x, y, clickCount, popup, button);
    }
    public int getX() { return x; }
    public int getY() { return y; }
    public int getXOnScreen() { return x; }
    public int getYOnScreen() { return y; }
    public Point getPoint() { return new Point(x, y); }
    public Point getLocationOnScreen() { return new Point(x, y); }
    public void translatePoint(int dx, int dy) { x += dx; y += dy; }
    public int getButton() { return button; }
    public int getClickCount() { return clickCount; }
    public boolean isPopupTrigger() { return popup; }
}
