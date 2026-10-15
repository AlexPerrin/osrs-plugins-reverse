package net.runelite.client.game.chatbox;

import net.runelite.api.widgets.Widget;

/** Replaces RuneLite's ChatboxPanelManager (same name): no custom chatbox inputs on Android yet. */
public class ChatboxPanelManager {
    private ChatboxInput current;

    public void close() { current = null; }
    public void openInput(ChatboxInput input) { current = input; }
    public Widget getContainerWidget() { return null; }
    public boolean shouldTakeInput() { return false; }
    public ChatboxInput getCurrentInput() { return current; }
}
