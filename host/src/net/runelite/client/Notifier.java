package net.runelite.client;

import java.awt.Graphics2D;
import java.awt.TrayIcon;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.client.config.Notification;

/**
 * Replaces RuneLite's Notifier (same name; the original needs javax.sound and the system tray).
 * Notifications go to the game chat as game messages, on the game thread, without a ChatMessage
 * event (desktop notifications never reach the chat): plugins that notify on a chat line (Cannon's
 * "Your cannon is out of ammo!") would otherwise match their own notification and loop.
 */
@Singleton
public class Notifier {
    private final Client client;

    @Inject
    public Notifier(Client client) {
        this.client = client;
    }

    public void notify(String message) {
        notify(message, TrayIcon.MessageType.NONE);
    }

    public void notify(String message, TrayIcon.MessageType type) {
        android.util.Log.i("osrsrl-host", "notify: " + message);
        net.runelite.host.Host.invoke(() -> client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", "[RuneLite] " + message, null, false));
    }

    public void notify(Notification notification, String message) {
        if (notification != null && !notification.isEnabled()) return;
        notify(message);
    }

    public void processFlash(Graphics2D g) {}
}
