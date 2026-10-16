package net.runelite.host;

import java.awt.Color;
import java.awt.image.BufferedImage;
import net.runelite.api.SpritePixels;

/**
 * A plain SpritePixels (Client.createSpritePixels): plugins build sprite overrides with it; they
 * are kept host-side, the game's sprites aren't replaced. Pixel 0 is transparent, as in the game.
 */
final class HostSpritePixels implements SpritePixels {
    private final int[] pixels;
    private final int width, height;
    private int maxWidth, maxHeight, offsetX, offsetY;

    HostSpritePixels(int[] pixels, int width, int height) {
        this.pixels = pixels;
        this.width = width;
        this.height = height;
        this.maxWidth = width;
        this.maxHeight = height;
    }

    @Override public void drawAt(int x, int y) {}
    @Override public int getWidth() { return width; }
    @Override public int getHeight() { return height; }
    @Override public int getMaxWidth() { return maxWidth; }
    @Override public int getMaxHeight() { return maxHeight; }
    @Override public int getOffsetX() { return offsetX; }
    @Override public int getOffsetY() { return offsetY; }
    @Override public void setMaxWidth(int v) { maxWidth = v; }
    @Override public void setMaxHeight(int v) { maxHeight = v; }
    @Override public void setOffsetX(int v) { offsetX = v; }
    @Override public void setOffsetY(int v) { offsetY = v; }
    @Override public int[] getPixels() { return pixels; }

    @Override public BufferedImage toBufferedImage() {
        BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        toBufferedImage(img);
        return img;
    }

    @Override public void toBufferedImage(BufferedImage img) {
        if (img.getWidth() != width || img.getHeight() != height) throw new IllegalArgumentException("size mismatch");
        for (int y = 0; y < height; y++)
            for (int x = 0; x < width; x++) {
                int p = pixels[y * width + x];
                img.setRGB(x, y, p == 0 ? 0 : 0xff000000 | p);
            }
    }

    @Override public BufferedImage toBufferedOutline(Color color) {
        BufferedImage img = new BufferedImage(width + 2, height + 2, BufferedImage.TYPE_INT_ARGB);
        toBufferedOutline(img, color.getRGB());
        return img;
    }

    @Override public void toBufferedOutline(BufferedImage img, int color) {
        for (int y = 0; y < height; y++)
            for (int x = 0; x < width; x++) {
                if (pixels[y * width + x] != 0) continue;
                boolean edge = (x > 0 && pixels[y * width + x - 1] != 0) || (x + 1 < width && pixels[y * width + x + 1] != 0)
                        || (y > 0 && pixels[(y - 1) * width + x] != 0) || (y + 1 < height && pixels[(y + 1) * width + x] != 0);
                if (edge) img.setRGB(x + 1, y + 1, color);
            }
    }
}
