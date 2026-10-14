package java.awt;

/** Android shim of java.awt.Cursor (no pointer on touch screens). */
public class Cursor {
    public static final int DEFAULT_CURSOR = 0, HAND_CURSOR = 12, MOVE_CURSOR = 13;
    private final int type;
    public Cursor(int type) { this.type = type; }
    public static Cursor getPredefinedCursor(int type) { return new Cursor(type); }
    public static Cursor getDefaultCursor() { return new Cursor(DEFAULT_CURSOR); }
    public int getType() { return type; }
}
