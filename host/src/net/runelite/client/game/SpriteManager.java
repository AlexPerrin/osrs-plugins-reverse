package net.runelite.client.game;

import java.awt.image.BufferedImage;
import java.util.function.Consumer;

/** Replaces RuneLite's SpriteManager (same name). Sprites aren't read from the cache yet. */
public class SpriteManager {
    public BufferedImage getSprite(int archive, int file) { return null; }
    public void getSpriteAsync(int archive, int file, Consumer<BufferedImage> user) {}
    public void getSpriteAsync(int archive, int file, net.runelite.client.ui.overlay.infobox.InfoBox infoBox) {}
    public void addSpriteOverrides(SpriteOverride[] overrides) {}
    public void removeSpriteOverrides(SpriteOverride[] overrides) {}
}
