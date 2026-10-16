package java.awt.event;

import java.awt.Component;

/** Android shim of java.awt.event.InputEvent. */
public abstract class InputEvent extends ComponentEvent {
    public static final int SHIFT_MASK = 1, CTRL_MASK = 2, META_MASK = 4, ALT_MASK = 8, ALT_GRAPH_MASK = 32,
            BUTTON1_MASK = 16, BUTTON2_MASK = 8, BUTTON3_MASK = 4;
    public static final int SHIFT_DOWN_MASK = 64, CTRL_DOWN_MASK = 128, META_DOWN_MASK = 256, ALT_DOWN_MASK = 512,
            BUTTON1_DOWN_MASK = 1024, BUTTON2_DOWN_MASK = 2048, BUTTON3_DOWN_MASK = 4096, ALT_GRAPH_DOWN_MASK = 8192;
    private final long when;
    private final int modifiers;

    InputEvent(Component source, int id, long when, int modifiers) {
        super(source, id);
        this.when = when;
        this.modifiers = modifiers;
    }
    public long getWhen() { return when; }
    public int getModifiers() { return modifiers & 0x3f; }
    public int getModifiersEx() { return modifiers & ~0x3f; }
    public boolean isShiftDown() { return (modifiers & (SHIFT_MASK | SHIFT_DOWN_MASK)) != 0; }
    public boolean isControlDown() { return (modifiers & (CTRL_MASK | CTRL_DOWN_MASK)) != 0; }
    public boolean isMetaDown() { return (modifiers & (META_MASK | META_DOWN_MASK)) != 0; }
    public boolean isAltDown() { return (modifiers & (ALT_MASK | ALT_DOWN_MASK)) != 0; }
    public boolean isAltGraphDown() { return (modifiers & (ALT_GRAPH_MASK | ALT_GRAPH_DOWN_MASK)) != 0; }
    @Override public void consume() { super.consume(); }
    @Override public boolean isConsumed() { return super.isConsumed(); }
    public static String getModifiersExText(int modifiers) { return ""; }

    /** As the JDK: the extended modifier mask of mouse button 1..3. */
    public static int getMaskForButton(int button) {
        if (button < 1 || button > 3) throw new IllegalArgumentException("button doesn't exist " + button);
        return button == 1 ? BUTTON1_DOWN_MASK : button == 2 ? BUTTON2_DOWN_MASK : BUTTON3_DOWN_MASK;
    }
}
