package net.runelite.host;

/**
 * A game font by id (Widget.getFont). getTextWidth uses the game's own measure (the one CS2
 * PARAWIDTH uses, Native.textWidth) and getBaseline the game font's ascent; both work on the game thread
 * only, elsewhere they return -1.
 */
final class GameFont implements net.runelite.api.FontTypeFace {
    private static final java.util.Map<Integer, GameFont> FONTS = new java.util.concurrent.ConcurrentHashMap<>();
    private final int id;

    private GameFont(int id) { this.id = id; }

    static GameFont of(int id) { return FONTS.computeIfAbsent(id, GameFont::new); }

    @Override public int getTextWidth(String text) {
        return text == null ? 0 : Native.textWidth(id, text);
    }

    /** The game font's ascent (its metrics file), as desktop's; -1 off the game thread or when the font isn't loaded. */
    @Override public int getBaseline() { return Native.fontAscent(id); }

    /**
     * Desktop's Font.drawLines into the Rasterizer's pixels (AndroidRender BUFFER, the copy of the captured frame;
     * the screen is the game's GPU frame): lines split at {@code <br>} and wrapped to the width, aligned
     * (x 0 left, 1 centre, 2 right; y 0 top, 1 middle, 2 bottom), shadow -1 for none, alpha 0..256 (256 or 0
     * opaque). The glyphs are RuneLite's bundled RuneScape TTF of the game font's style, not the game's sprite font.
     */
    @Override public void drawWidgetText(String text, int x, int y, int width, int height, int rgb, int shadow, int alpha, int xAlign, int yAlign, int lineHeight) {
        if (text == null || text.isEmpty()) return;
        java.awt.Graphics2D g = AndroidRender.rasterGraphics();
        try {
            g.setFont(awtFont());
            java.awt.FontMetrics fm = g.getFontMetrics(g.getFont());
            int asc = getBaseline(), base = asc > 0 ? asc : fm.getAscent();
            if (lineHeight == 0) lineHeight = base;
            java.util.List<String> lines = new java.util.ArrayList<>();
            for (String para : net.runelite.client.util.Text.removeTags(text.replace("<br>", "\n")).split("\n", -1)) wrap(para, width, fm, lines);
            int top = yAlign == 1 ? y + (height - lineHeight * (lines.size() - 1) - base) / 2 : yAlign == 2 ? y + height - lineHeight * (lines.size() - 1) - base : y;
            int a = alpha <= 0 || alpha >= 256 ? 255 : alpha;
            for (int i = 0; i < lines.size(); i++) {
                String l = lines.get(i);
                int w = fm.stringWidth(l);
                int lx = xAlign == 1 ? x + (width - w) / 2 : xAlign == 2 ? x + width - w : x;
                int ly = top + base + i * lineHeight;
                if (shadow != -1) {
                    g.setColor(new java.awt.Color(a << 24 | shadow & 0xffffff, true));
                    g.drawString(l, lx + 1, ly + 1);
                }
                g.setColor(new java.awt.Color(a << 24 | rgb & 0xffffff, true));
                g.drawString(l, lx, ly);
            }
        } finally {
            g.dispose();
        }
    }

    private static void wrap(String para, int width, java.awt.FontMetrics fm, java.util.List<String> out) {
        if (width <= 0 || fm.stringWidth(para) <= width) { out.add(para); return; }
        StringBuilder line = new StringBuilder();
        for (String word : para.split(" ")) {
            if (line.length() > 0 && fm.stringWidth(line + " " + word) > width) { out.add(line.toString()); line.setLength(0); }
            if (line.length() > 0) line.append(' ');
            line.append(word);
        }
        out.add(line.toString());
    }

    /** RuneLite's TTF for the game font's style (FontID: 494 plain 11, 496 bold 12, others plain 12). */
    private java.awt.Font awtFont() {
        return id == 494 ? net.runelite.client.ui.FontManager.getRunescapeSmallFont()
            : id == 496 ? net.runelite.client.ui.FontManager.getRunescapeBoldFont()
            : net.runelite.client.ui.FontManager.getRunescapeFont();
    }

    @Override public String toString() { return "GameFont[" + id + "]"; }
}
