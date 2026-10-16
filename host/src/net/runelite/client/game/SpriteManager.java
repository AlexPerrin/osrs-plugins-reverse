package net.runelite.client.game;

import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;
import net.runelite.host.Host;
import net.runelite.host.HostSprites;

/**
 * Replaces RuneLite's SpriteManager (same name). Sprites come from the game's own sprite cache
 * (frame 0 of a graphic id, HostSprites); loaded images are kept here. getSprite off the client
 * thread, or while the sprites archive is still loading, returns a transparent placeholder
 * (code that measures or composes sprites keeps working) and queues a load; the async variants
 * retry on the client thread until the sprite is available.
 */
public class SpriteManager {
    private static final Map<Long, BufferedImage> CACHE = new HashMap<>();
    private static final int RETRIES = 250;

    private static long key(int archive, int file) { return (long) archive << 32 | (file & 0xffffffffL); }

    public BufferedImage getSprite(int archive, int file) {
        BufferedImage img = cachedOrLoad(archive, file);
        if (img != null) return img;
        if (!HostSprites.onClientThread()) Host.invoke(() -> cachedOrLoad(archive, file));
        return new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
    }

    /** Cached image, or a load when on the client thread; null if not (yet) available. */
    private static BufferedImage cachedOrLoad(int archive, int file) {
        synchronized (CACHE) {
            BufferedImage img = CACHE.get(key(archive, file));
            if (img != null) return img;
        }
        if (file != 0 || !HostSprites.onClientThread()) return null;
        BufferedImage img = HostSprites.load(archive);
        if (img != null) synchronized (CACHE) { CACHE.put(key(archive, file), img); }
        return img;
    }

    public void getSpriteAsync(int archive, int file, Consumer<BufferedImage> user) {
        int[] left = {RETRIES};
        Host.invokeUntil(() -> {
            BufferedImage img = cachedOrLoad(archive, file);
            if (img != null) user.accept(img);
            return img != null || file != 0 || --left[0] <= 0;
        });
    }

    public void getSpriteAsync(int archive, int file, net.runelite.client.ui.overlay.infobox.InfoBox infoBox) {
        getSpriteAsync(archive, file, infoBox::setImage);
    }

    public void addSpriteTo(javax.swing.JButton c, int archive, int file) {
        getSpriteAsync(archive, file, img -> javax.swing.SwingUtilities.invokeLater(() -> c.setIcon(new javax.swing.ImageIcon(img))));
    }

    public void addSpriteTo(javax.swing.JLabel c, int archive, int file) {
        getSpriteAsync(archive, file, img -> javax.swing.SwingUtilities.invokeLater(() -> c.setIcon(new javax.swing.ImageIcon(img))));
    }

    /** As RuneLite: resource images into Client.getSpriteOverrides (written into the game's sprites). */
    public void addSpriteOverrides(SpriteOverride[] add) {
        if (add.length == 0) return;
        Host.invoke(() -> {
            net.runelite.api.Client client = Host.client();
            Class<?> owner = add[0].getClass();
            for (SpriteOverride o : add) {
                BufferedImage image = net.runelite.client.util.ImageUtil.loadImageResource(owner, o.getFileName());
                client.getSpriteOverrides().put(o.getSpriteId(), net.runelite.client.util.ImageUtil.getImageSpritePixels(image, client));
            }
        });
    }

    public void removeSpriteOverrides(SpriteOverride[] remove) {
        Host.invoke(() -> {
            for (SpriteOverride o : remove) Host.client().getSpriteOverrides().remove(o.getSpriteId());
        });
    }
}
