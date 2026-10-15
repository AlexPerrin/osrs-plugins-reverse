package java.awt.event;

import java.awt.Component;

/** Android shim of java.awt.event.KeyEvent (subset of key codes). */
public class KeyEvent extends InputEvent {
    public static final int KEY_TYPED = 400, KEY_PRESSED = 401, KEY_RELEASED = 402;
    public static final int KEY_LOCATION_UNKNOWN = 0, KEY_LOCATION_STANDARD = 1;
    public static final char CHAR_UNDEFINED = 0xFFFF;
    public static final int VK_UNDEFINED = 0, VK_ENTER = '\n', VK_BACK_SPACE = '\b', VK_TAB = '\t', VK_ESCAPE = 0x1B,
            VK_SPACE = 0x20, VK_SHIFT = 0x10, VK_CONTROL = 0x11, VK_ALT = 0x12, VK_UP = 0x26, VK_DOWN = 0x28,
            VK_LEFT = 0x25, VK_RIGHT = 0x27, VK_DELETE = 0x7F, VK_HOME = 0x24, VK_END = 0x23, VK_PAGE_UP = 0x21,
            VK_PAGE_DOWN = 0x22, VK_F1 = 0x70, VK_F12 = 0x7B, VK_A = 0x41, VK_Z = 0x5A, VK_0 = 0x30, VK_9 = 0x39;
    private int keyCode;
    private char keyChar;

    public KeyEvent(Component source, int id, long when, int modifiers, int keyCode, char keyChar) {
        super(source, id, when, modifiers);
        this.keyCode = keyCode;
        this.keyChar = keyChar;
    }
    public KeyEvent(Component source, int id, long when, int modifiers, int keyCode, char keyChar, int location) {
        this(source, id, when, modifiers, keyCode, keyChar);
    }
    public int getKeyCode() { return keyCode; }
    public void setKeyCode(int keyCode) { this.keyCode = keyCode; }
    public char getKeyChar() { return keyChar; }
    public void setKeyChar(char keyChar) { this.keyChar = keyChar; }
    public int getKeyLocation() { return KEY_LOCATION_STANDARD; }
    public int getExtendedKeyCode() { return keyCode; }
    public boolean isActionKey() { return false; }
    public static String getKeyText(int keyCode) { return keyCode >= 0x20 && keyCode < 0x7f ? String.valueOf((char) keyCode) : "Key " + keyCode; }
    public static int getExtendedKeyCodeForChar(int c) { return Character.toUpperCase(c); }
}
