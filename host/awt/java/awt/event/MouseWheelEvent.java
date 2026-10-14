package java.awt.event;

/** Android shim of java.awt.event.MouseWheelEvent (subset). */
public class MouseWheelEvent extends MouseEvent {
    private final int wheelRotation;
    public MouseWheelEvent(Object source, int id, long when, int modifiers, int x, int y, int clickCount, boolean popup,
                           int scrollType, int scrollAmount, int wheelRotation) {
        super(source, id, when, modifiers, x, y, clickCount, popup, NOBUTTON);
        this.wheelRotation = wheelRotation;
    }
    public int getWheelRotation() { return wheelRotation; }
    public double getPreciseWheelRotation() { return wheelRotation; }
}
