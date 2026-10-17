package net.runelite.host;

/**
 * A game font by id (Widget.getFont). getTextWidth uses the game's own measure (the one CS2
 * PARAWIDTH uses, Native.textWidth) and works on the game thread only; elsewhere it returns -1.
 */
final class GameFont implements net.runelite.api.FontTypeFace {
    private static final java.util.Map<Integer, GameFont> FONTS = new java.util.concurrent.ConcurrentHashMap<>();
    private final int id;

    private GameFont(int id) { this.id = id; }

    static GameFont of(int id) { return FONTS.computeIfAbsent(id, GameFont::new); }

    @Override public int getTextWidth(String text) {
        return text == null ? 0 : Native.textWidth(id, text);
    }

    /** Not read from the game font yet. */
    @Override public int getBaseline() {
        throw new UnsupportedOperationException("FontTypeFace.getBaseline");
    }

    /** Plugins draw through overlays on Android; drawing into the game's raster isn't supported. */
    @Override public void drawWidgetText(String text, int x, int y, int width, int height, int rgb, int shadow, int alpha, int xAlign, int yAlign, int lineHeight) {
        throw new UnsupportedOperationException("FontTypeFace.drawWidgetText");
    }

    @Override public String toString() { return "GameFont[" + id + "]"; }
}
