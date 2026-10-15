package net.runelite.client.game;

import java.awt.image.BufferedImage;
import java.util.function.Consumer;

/**
 * Replaces RuneLite's SpriteManager (same name). Sprites aren't read from the cache yet: callers
 * get a transparent placeholder so code that measures or composes sprites keeps working.
 */
public class SpriteManager {
    public BufferedImage getSprite(int archive, int file) { return new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB); }
    public void getSpriteAsync(int archive, int file, Consumer<BufferedImage> user) { user.accept(getSprite(archive, file)); }
    public void getSpriteAsync(int archive, int file, net.runelite.client.ui.overlay.infobox.InfoBox infoBox) {}
    public void addSpriteOverrides(SpriteOverride[] overrides) {}
    public void removeSpriteOverrides(SpriteOverride[] overrides) {}
}
