package java.awt.event;

import java.awt.Component;

/** Android shim of java.awt.event.MouseWheelEvent. */
public class MouseWheelEvent extends MouseEvent {
    public static final int WHEEL_UNIT_SCROLL = 0, WHEEL_BLOCK_SCROLL = 1;
    private final int scrollType, scrollAmount, wheelRotation;

    public MouseWheelEvent(Component source, int id, long when, int modifiers, int x, int y, int clickCount, boolean popup,
                           int scrollType, int scrollAmount, int wheelRotation) {
        super(source, id, when, modifiers, x, y, clickCount, popup, NOBUTTON);
        this.scrollType = scrollType; this.scrollAmount = scrollAmount; this.wheelRotation = wheelRotation;
    }
    public int getScrollType() { return scrollType; }
    public int getScrollAmount() { return scrollAmount; }
    public int getWheelRotation() { return wheelRotation; }
    public double getPreciseWheelRotation() { return wheelRotation; }
    public int getUnitsToScroll() { return scrollAmount * wheelRotation; }
}
