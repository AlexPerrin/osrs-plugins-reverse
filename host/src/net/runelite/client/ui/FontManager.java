package net.runelite.client.ui;

import java.awt.Font;
import java.awt.GraphicsEnvironment;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Replaces RuneLite's FontManager (which needs Swing's StyleContext): loads the bundled
 * RuneScape fonts through the java.awt shim.
 */
public class FontManager {
    private static final Font runescapeFont, runescapeSmallFont, runescapeBoldFont, defaultFont, defaultBoldFont;

    static {
        runescapeFont = load("runescape.ttf", "RuneScape");
        runescapeSmallFont = load("runescape_small.ttf", "RuneScape Small");
        runescapeBoldFont = load("runescape_bold.ttf", "RuneScape Bold");
        defaultFont = new Font(Font.DIALOG, Font.PLAIN, 12);
        defaultBoldFont = new Font(Font.DIALOG, Font.BOLD, 12);
    }

    private static Font load(String file, String name) {
        try (InputStream in = FontManager.class.getResourceAsStream(file)) {
            if (in == null) throw new IllegalStateException("missing " + file);
            Font f = Font.createFont(Font.TRUETYPE_FONT, in).deriveFont(Font.PLAIN, 16f);
            GraphicsEnvironment.getLocalGraphicsEnvironment().registerFont(f);
            return f;
        } catch (Exception e) {
            android.util.Log.e("osrsrl-host", "font " + file + " failed", e);
            return new Font(Font.DIALOG, Font.PLAIN, 16);
        }
    }

    public FontManager() {}

    public static List<String> getBuiltInFonts() { return Arrays.asList("RuneScape", "RuneScape Small", "RuneScape Bold"); }
    public static List<String> getSystemFonts() { return Arrays.asList(Font.DIALOG, Font.SANS_SERIF, Font.SERIF, Font.MONOSPACED); }
    public static List<String> getCustomFonts() { return Collections.emptyList(); }
    public static Font getFallbackFont(String name, int style, int size) { return new Font(Font.DIALOG, style, size); }
    public static Font getRunescapeFont() { return runescapeFont; }
    public static Font getRunescapeSmallFont() { return runescapeSmallFont; }
    public static Font getRunescapeBoldFont() { return runescapeBoldFont; }
    public static Font getDefaultFont() { return defaultFont; }
    public static Font getDefaultBoldFont() { return defaultBoldFont; }
}
