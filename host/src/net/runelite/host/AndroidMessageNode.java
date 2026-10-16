package net.runelite.host;

import net.runelite.api.ChatMessageType;

/**
 * A chat message as captured when the game added it. setValue also rewrites the game's
 * message text; the other setters change only this copy.
 */
public class AndroidMessageNode extends MessageNodeStub {
    private final int id;
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
    }

    @Override public int getId() { return id; }
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
        if (Native.isClientThread()) Native.messageSetText(id, v);
        else Host.invoke(() -> Native.messageSetText(id, v));
    }
    @Override public String getRuneLiteFormatMessage() { return runeLiteFormat; }
    @Override public void setRuneLiteFormatMessage(String m) { runeLiteFormat = m; }
    @Override public int getTimestamp() { return timestamp; }
    @Override public void setTimestamp(int t) { timestamp = t; }
}
