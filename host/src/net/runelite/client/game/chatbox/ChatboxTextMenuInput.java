package net.runelite.client.game.chatbox;

import java.util.ArrayList;
import java.util.List;

/**
 * Replaces RuneLite's ChatboxTextMenuInput (same name): build() shows the options in an Android list
 * dialog (net.runelite.host.AndroidPrompts.menu) instead of the chatbox. Callbacks run on the client
 * thread.
 */
public class ChatboxTextMenuInput extends ChatboxInput implements net.runelite.client.input.KeyListener {
    public static final class Entry {
        private final String text;
        private final Runnable callback;
        Entry(String text, Runnable callback) { this.text = text; this.callback = callback; }
        public String getText() { return text; }
        public Runnable getCallback() { return callback; }
    }

    private final ChatboxPanelManager chatboxPanelManager;
    private String title = "";
    private final List<Entry> options = new ArrayList<>();
    private Runnable onClose;

    @javax.inject.Inject
    protected ChatboxTextMenuInput(ChatboxPanelManager chatboxPanelManager) {
        this.chatboxPanelManager = chatboxPanelManager;
    }

    public ChatboxTextMenuInput title(String t) { title = t != null ? t : ""; return this; }
    public ChatboxTextMenuInput option(String text, Runnable callback) { options.add(new Entry(text, callback)); return this; }
    public ChatboxTextMenuInput onClose(Runnable r) { onClose = r; return this; }

    public ChatboxTextMenuInput build() {
        if (title == null) throw new IllegalStateException("Title must be set");
        if (options.isEmpty()) throw new IllegalStateException("You must have at least 1 option");
        chatboxPanelManager.openInput(this);
        return this;
    }

    @Override protected void open() {
        final ChatboxTextMenuInput self = this;
        String[] labels = new String[options.size()];
        for (int i = 0; i < labels.length; i++) labels[i] = net.runelite.client.util.Text.removeTags(options.get(i).getText());
        net.runelite.host.AndroidPrompts.menu(net.runelite.client.util.Text.removeTags(title), labels,
            i -> {
                if (chatboxPanelManager.getCurrentInput() == self) chatboxPanelManager.close();
                if (i >= 0 && i < options.size() && options.get(i).getCallback() != null) options.get(i).getCallback().run();
            },
            () -> { if (chatboxPanelManager.getCurrentInput() == self) chatboxPanelManager.close(); });
    }

    @Override protected void close() {
        if (onClose != null) onClose.run();
    }

    public void keyTyped(java.awt.event.KeyEvent e) {}
    public void keyPressed(java.awt.event.KeyEvent e) {}
    public void keyReleased(java.awt.event.KeyEvent e) {}

    public String getTitle() { return title; }
    public List<Entry> getOptions() { return options; }
    public Runnable getOnClose() { return onClose; }
}
