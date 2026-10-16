package net.runelite.client.chat;

import android.util.Log;
import net.runelite.api.ChatMessageType;
import net.runelite.api.MessageNode;

/**
 * Replaces RuneLite's ChatMessageManager (same name). Queued messages go into the game's chatbox
 * through Client.addChatMessage (so they also come back as ChatMessage events). RuneLite's colour
 * placeholders (<colNORMAL>, <colHIGHLIGHT>, ...) become the transparent-chatbox defaults, as the
 * mobile chatbox is drawn over the scene.
 */
public class ChatMessageManager {
    public void queue(QueuedMessage message) {
        net.runelite.api.Client client = net.runelite.host.Host.client();
        if (client == null || message == null) return;
        String text = message.getRuneLiteFormattedMessage() != null
            ? format(message.getRuneLiteFormattedMessage()) : message.getValue();
        if (text == null) return;
        ChatMessageType type = message.getType() != null ? message.getType() : ChatMessageType.CONSOLE;
        // The game's CONSOLE type isn't shown in the mobile chatbox; GAMEMESSAGE is.
        if (type == ChatMessageType.CONSOLE) type = ChatMessageType.GAMEMESSAGE;
        Log.i("osrsrl-host", "chat (queued by plugin): " + text);
        client.addChatMessage(type, message.getName() != null ? message.getName() : "", text, message.getSender());
    }

    /** RuneLite format -> game tags, with ChatColorConfig's transparent-chatbox game-message defaults. */
    public static String format(String s) {
        return s.replace("<col" + ChatColorType.NORMAL.name() + ">", "<col=ffffff>")
                .replace("<col" + ChatColorType.HIGHLIGHT.name() + ">", "<col=ef5050>");
    }

    public void process() {}
    public void update(MessageNode node) {
        if (node != null && node.getRuneLiteFormatMessage() != null) node.setValue(format(node.getRuneLiteFormatMessage()));
    }
}
