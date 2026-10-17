package net.runelite.host;

import net.runelite.api.ChatMessageType;

/**
 * A chat message as captured when the game added it. setValue also rewrites the game's
 * message text; the other setters change only this copy.
 */
public class AndroidMessageNode extends MessageNodeStub {
    private int id;
    final boolean postEvent;
    /** For a pending node: the text given to the game (matched against its new message). */
    final String sentText;
    long pendingSince;
    private final ChatMessageType type;
    private String name, sender, value, runeLiteFormat;
    private int timestamp;

    AndroidMessageNode(int id, ChatMessageType type, String name, String sender, String value, int timestamp) {
        this.id = id;
        this.type = type;
        this.name = name;
        this.sender = sender;
        this.value = value;
        this.timestamp = timestamp;
        this.postEvent = true;
        this.sentText = null;
    }

    /**
     * A node returned by Client.addChatMessage before the game's message exists (id -1); it is
     * bound to the game's message when that arrives (Host CHAT_MESSAGE), and a value set before
     * then is written into it.
     */
    AndroidMessageNode(ChatMessageType type, String name, String value, String sender, boolean postEvent) {
        this.id = -1;
        this.type = type;
        this.name = name;
        this.sender = sender;
        this.value = value;
        this.timestamp = (int) (System.currentTimeMillis() / 1000);
        this.postEvent = postEvent;
        this.sentText = value;
        this.pendingSince = System.currentTimeMillis();
    }

    boolean pending() { return id < 0; }

    /** Binds a pending node to the game's message; returns the text the game shows. */
    void bind(int id, String gameText) {
        this.id = id;
        if (value != null && !value.equals(gameText)) Native.messageSetText(id, value);
        else value = gameText;
    }

    @Override public int getId() { return id; }
    // Node: the hash is the key the game's table holds it under (container id / message id); these
    // snapshots aren't linked into a table, so next/previous are null.
    @Override public long getHash() { return id; }
    @Override public net.runelite.api.Node getNext() { return null; }
    @Override public net.runelite.api.Node getPrevious() { return null; }
    @Override public ChatMessageType getType() { return type; }
    @Override public String getName() { return name; }
    @Override public void setName(String name) { this.name = name; }
    @Override public String getSender() { return sender; }
    @Override public void setSender(String sender) { this.sender = sender; }
    @Override public String getValue() { return value; }
    /** Also written into the game's message (shown when the chatbox next redraws). */
    @Override public void setValue(String value) {
        this.value = value;
        final String v = value != null ? value : "";
        if (id < 0) return;
        final int i = id;
        if (Native.isClientThread()) Native.messageSetText(i, v);
        else Host.invoke(() -> Native.messageSetText(i, v));
    }
    @Override public String getRuneLiteFormatMessage() { return runeLiteFormat; }
    @Override public void setRuneLiteFormatMessage(String m) { runeLiteFormat = m; }
    @Override public int getTimestamp() { return timestamp; }
    @Override public void setTimestamp(int t) { timestamp = t; }
}
