package java.awt;

import java.util.HashMap;
import java.util.Map;

/** Android shim of java.awt.RenderingHints (a plain map; antialiasing is honoured). */
public class RenderingHints extends HashMap<Object, Object> {
    public abstract static class Key {
        private final int key;
        protected Key(int key) { this.key = key; }
        public abstract boolean isCompatibleValue(Object val);
        protected final int intKey() { return key; }
    }

    private static final class K extends Key {
        private final String name;
        K(int key, String name) { super(key); this.name = name; }
        @Override public boolean isCompatibleValue(Object val) { return true; }
        @Override public String toString() { return name; }
    }

    private static Key k(int i, String name) { return new K(i, name); }
    private static Object v(String name) { return name; }

    public static final Key KEY_ANTIALIASING = k(1, "Antialiasing"), KEY_RENDERING = k(2, "Rendering"),
            KEY_DITHERING = k(3, "Dithering"), KEY_TEXT_ANTIALIASING = k(4, "Text antialiasing"),
            KEY_TEXT_LCD_CONTRAST = k(5, "Text LCD contrast"), KEY_FRACTIONALMETRICS = k(6, "Fractional metrics"),
            KEY_INTERPOLATION = k(7, "Interpolation"), KEY_ALPHA_INTERPOLATION = k(8, "Alpha interpolation"),
            KEY_COLOR_RENDERING = k(9, "Color rendering"), KEY_STROKE_CONTROL = k(10, "Stroke control");
    public static final Object VALUE_ANTIALIAS_ON = v("Antialias on"), VALUE_ANTIALIAS_OFF = v("Antialias off"),
            VALUE_ANTIALIAS_DEFAULT = v("Antialias default"), VALUE_RENDER_SPEED = v("Render speed"),
            VALUE_RENDER_QUALITY = v("Render quality"), VALUE_RENDER_DEFAULT = v("Render default"),
            VALUE_TEXT_ANTIALIAS_ON = v("Text antialias on"), VALUE_TEXT_ANTIALIAS_OFF = v("Text antialias off"),
            VALUE_TEXT_ANTIALIAS_DEFAULT = v("Text antialias default"), VALUE_TEXT_ANTIALIAS_GASP = v("Text antialias GASP"), VALUE_FRACTIONALMETRICS_ON = v("Fractional on"),
            VALUE_FRACTIONALMETRICS_OFF = v("Fractional off"), VALUE_INTERPOLATION_NEAREST_NEIGHBOR = v("Nearest"),
            VALUE_INTERPOLATION_BILINEAR = v("Bilinear"), VALUE_INTERPOLATION_BICUBIC = v("Bicubic"),
            VALUE_STROKE_PURE = v("Stroke pure"), VALUE_STROKE_NORMALIZE = v("Stroke normalize"),
            VALUE_STROKE_DEFAULT = v("Stroke default"), VALUE_COLOR_RENDER_QUALITY = v("Color quality"),
            VALUE_ALPHA_INTERPOLATION_QUALITY = v("Alpha quality");

    public RenderingHints(Key key, Object value) { if (key != null) put(key, value); }
    public RenderingHints(Map<Key, ?> init) { if (init != null) putAll(init); }
}
