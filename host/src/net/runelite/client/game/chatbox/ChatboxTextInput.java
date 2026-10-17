package net.runelite.client.game.chatbox;

import java.util.function.Consumer;
import java.util.function.IntPredicate;
import java.util.function.Predicate;

/**
 * Replaces RuneLite's ChatboxTextInput (same name). The desktop version draws a text field in the
 * chatbox and reads the keyboard; here build() opens an Android text dialog
 * (net.runelite.host.AndroidPrompts): edits go to onChanged, OK to onDone, and closing it to onClose
 * and ChatboxPanelManager.close(). Callbacks run on the client thread.
 */
public class ChatboxTextInput extends ChatboxInput implements net.runelite.client.input.KeyListener, net.runelite.client.input.MouseListener {
    private final ChatboxPanelManager chatboxPanelManager;
    private String prompt = "";
    private String value = "";
    private int lines = 1;
    private int cursorStart, cursorEnd;
    private IntPredicate charValidator = c -> c >= 32 && c != 127;
    private Runnable onClose;
    private Predicate<String> onDone;
    private Consumer<String> onChanged;
    private int fontID = 495;
    private boolean built;

    protected final net.runelite.client.callback.ClientThread clientThread;

    protected ChatboxTextInput(ChatboxPanelManager chatboxPanelManager) {
        this(chatboxPanelManager, null);
    }

    /** RuneLite's constructor: subclasses (ChatboxItemSearch, used by Bank Tags) call it. */
    protected ChatboxTextInput(ChatboxPanelManager chatboxPanelManager, net.runelite.client.callback.ClientThread clientThread) {
        this.chatboxPanelManager = chatboxPanelManager;
        this.clientThread = clientThread;
    }

    /** Desktop redraws the chatbox field here; the Android dialog needs nothing (subclasses call super). */
    protected void update() {}
    protected void buildEdit(int x, int y, int w, int h) {}

    public ChatboxTextInput addCharValidator(IntPredicate v) { charValidator = charValidator.and(v); return this; }
    public ChatboxTextInput lines(int n) { lines = n; return this; }
    public ChatboxTextInput prompt(String p) { prompt = p != null ? p : ""; return this; }
    public ChatboxTextInput value(String v) {
        value = v != null ? v : "";
        cursorStart = cursorEnd = value.length();
        return this;
    }
    public ChatboxTextInput cursorAt(int i) { cursorStart = cursorEnd = i; return this; }
    public ChatboxTextInput cursorAt(int s, int e) { cursorStart = s; cursorEnd = e; return this; }
    public String getValue() { return value; }
    public ChatboxTextInput charValidator(IntPredicate v) { charValidator = v; return this; }
    public ChatboxTextInput onClose(Runnable r) { onClose = r; return this; }
    public ChatboxTextInput onDone(Consumer<String> c) { onDone = s -> { c.accept(s); return true; }; return this; }
    public ChatboxTextInput onDone(Predicate<String> p) { onDone = p; return this; }
    public ChatboxTextInput onChanged(Consumer<String> c) { onChanged = c; return this; }
    public ChatboxTextInput fontID(int id) { fontID = id; return this; }

    public ChatboxTextInput build() {
        built = true;
        chatboxPanelManager.openInput(this);
        return this;
    }

    private String filter(String s) {
        StringBuilder b = new StringBuilder();
        s.codePoints().filter(charValidator).forEach(b::appendCodePoint);
        return b.toString();
    }

    @Override protected void open() {
        final ChatboxTextInput self = this;
        net.runelite.host.AndroidPrompts.text(prompt, value, lines,
            s -> {
                if (chatboxPanelManager.getCurrentInput() != self) return;
                value = filter(s);
                cursorStart = cursorEnd = value.length();
                if (onChanged != null) onChanged.accept(value);
            },
            s -> {
                value = filter(s);
                if (onDone != null) onDone.test(value);
            },
            () -> { if (chatboxPanelManager.getCurrentInput() == self) chatboxPanelManager.close(); });
    }

    @Override protected void close() {
        if (onClose != null) onClose.run();
    }

    public void keyTyped(java.awt.event.KeyEvent e) {}
    public void keyPressed(java.awt.event.KeyEvent e) {}
    public void keyReleased(java.awt.event.KeyEvent e) {}
    public java.awt.event.MouseEvent mouseClicked(java.awt.event.MouseEvent e) { return e; }
    public java.awt.event.MouseEvent mousePressed(java.awt.event.MouseEvent e) { return e; }
    public java.awt.event.MouseEvent mouseReleased(java.awt.event.MouseEvent e) { return e; }
    public java.awt.event.MouseEvent mouseEntered(java.awt.event.MouseEvent e) { return e; }
    public java.awt.event.MouseEvent mouseExited(java.awt.event.MouseEvent e) { return e; }
    public java.awt.event.MouseEvent mouseDragged(java.awt.event.MouseEvent e) { return e; }
    public java.awt.event.MouseEvent mouseMoved(java.awt.event.MouseEvent e) { return e; }

    public String getPrompt() { return prompt; }
    public int getLines() { return lines; }
    public int getCursorStart() { return cursorStart; }
    public int getCursorEnd() { return cursorEnd; }
    public IntPredicate getCharValidator() { return charValidator; }
    public Runnable getOnClose() { return onClose; }
    public Predicate<String> getOnDone() { return onDone; }
    public Consumer<String> getOnChanged() { return onChanged; }
    public int getFontID() { return fontID; }
    public boolean isBuilt() { return built; }
}
