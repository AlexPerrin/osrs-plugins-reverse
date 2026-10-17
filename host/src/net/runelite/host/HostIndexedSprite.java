package net.runelite.host;

import net.runelite.api.IndexedSprite;

/** A plain IndexedSprite: plugin chat icons, or a placeholder for a game icon (AndroidClient.getModIcons). */
final class HostIndexedSprite implements IndexedSprite {
    private byte[] pixels = new byte[0];
    private int[] palette = {0};
    private int offsetX, offsetY, width, height, originalWidth, originalHeight;

    /** Placeholder for the game's own icon `index` (its pixels stay in the game). */
    int gameIndex = -1;

    static HostIndexedSprite game(int index, int w, int h) {
        HostIndexedSprite s = blank(w, h);
        s.gameIndex = index;
        return s;
    }

    static HostIndexedSprite blank(int w, int h) {
        HostIndexedSprite s = new HostIndexedSprite();
        s.width = s.originalWidth = w;
        s.height = s.originalHeight = h;
        s.pixels = new byte[w * h];
        return s;
    }

    @Override public byte[] getPixels() { return pixels; }
    @Override public void setPixels(byte[] p) { pixels = p; }
    @Override public int[] getPalette() { return palette; }
    @Override public void setPalette(int[] p) { palette = p; }
    @Override public int getOffsetX() { return offsetX; }
    @Override public void setOffsetX(int v) { offsetX = v; }
    @Override public int getOffsetY() { return offsetY; }
    @Override public void setOffsetY(int v) { offsetY = v; }
    @Override public int getWidth() { return width; }
    @Override public void setWidth(int v) { width = v; }
    @Override public int getHeight() { return height; }
    @Override public void setHeight(int v) { height = v; }
    @Override public int getOriginalWidth() { return originalWidth; }
    @Override public void setOriginalWidth(int v) { originalWidth = v; }
    @Override public int getOriginalHeight() { return originalHeight; }
    @Override public void setOriginalHeight(int v) { originalHeight = v; }
}
