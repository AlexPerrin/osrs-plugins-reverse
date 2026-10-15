package net.runelite.client.chat;

import android.util.Log;
import net.runelite.api.MessageNode;

/**
 * Replaces RuneLite's ChatMessageManager (same name). Queued messages are logged; adding
 * them to the game's chatbox needs Client.addChatMessage (not implemented yet).
 */
public class ChatMessageManager {
    public void queue(QueuedMessage message) {
        Log.i("osrsrl-host", "chat (queued by plugin): " + message.getRuneLiteFormattedMessage() + " / " + message.getValue());
    }
    public void process() {}
    public void update(MessageNode node) {}
}
