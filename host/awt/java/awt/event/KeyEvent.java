package java.awt.event;

/** Android shim of java.awt.event.KeyEvent (subset). */
public class KeyEvent extends java.util.EventObject {
    public static final int KEY_TYPED = 400, KEY_PRESSED = 401, KEY_RELEASED = 402;
    public static final char CHAR_UNDEFINED = 0xFFFF;
    public static final int VK_UNDEFINED = 0, VK_ENTER = '\n', VK_BACK_SPACE = '\b', VK_TAB = '\t', VK_ESCAPE = 0x1B,
            VK_SPACE = 0x20, VK_SHIFT = 0x10, VK_CONTROL = 0x11, VK_ALT = 0x12;
    private final int id, keyCode, modifiers;
    private final char keyChar;
    private boolean consumed;

    public KeyEvent(Object source, int id, long when, int modifiers, int keyCode, char keyChar) {
        super(source);
        this.id = id; this.modifiers = modifiers; this.keyCode = keyCode; this.keyChar = keyChar;
    }
    public int getID() { return id; }
    public int getKeyCode() { return keyCode; }
    public char getKeyChar() { return keyChar; }
    public int getModifiersEx() { return modifiers; }
    public void consume() { consumed = true; }
    public boolean isConsumed() { return consumed; }
}
