package net.runelite.client.game.chatbox;

import net.runelite.api.widgets.Widget;

/**
 * Replaces RuneLite's ChatboxPanelManager (same name). There is no chatbox panel on Android: text and
 * menu inputs open Android dialogs (ChatboxTextInput, ChatboxTextMenuInput replacements). One input
 * is current at a time; opening another closes it, as on desktop.
 */
@javax.inject.Singleton
public class ChatboxPanelManager {
    private ChatboxInput current;

    public void close() {
        ChatboxInput c = current;
        current = null;
        if (c != null) c.close();
    }

    public void openInput(ChatboxInput input) {
        close();
        current = input;
        input.open();
    }

    public ChatboxTextInput openTextInput(String prompt) { return new ChatboxTextInput(this).prompt(prompt); }
    public ChatboxTextMenuInput openTextMenuInput(String title) { return new ChatboxTextMenuInput(this).title(title); }

    public Widget getContainerWidget() { return null; }
    public boolean shouldTakeInput() { return false; }
    public ChatboxInput getCurrentInput() { return current; }
}
