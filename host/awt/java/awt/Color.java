package java.awt;

/** Android shim of java.awt.Color (sRGB only). */
public class Color implements Paint {
    public static final Color white = new Color(255, 255, 255), WHITE = white;
    public static final Color lightGray = new Color(192, 192, 192), LIGHT_GRAY = lightGray;
    public static final Color gray = new Color(128, 128, 128), GRAY = gray;
    public static final Color darkGray = new Color(64, 64, 64), DARK_GRAY = darkGray;
    public static final Color black = new Color(0, 0, 0), BLACK = black;
    public static final Color red = new Color(255, 0, 0), RED = red;
    public static final Color pink = new Color(255, 175, 175), PINK = pink;
    public static final Color orange = new Color(255, 200, 0), ORANGE = orange;
    public static final Color yellow = new Color(255, 255, 0), YELLOW = yellow;
    public static final Color green = new Color(0, 255, 0), GREEN = green;
    public static final Color magenta = new Color(255, 0, 255), MAGENTA = magenta;
    public static final Color cyan = new Color(0, 255, 255), CYAN = cyan;
    public static final Color blue = new Color(0, 0, 255), BLUE = blue;

    private final int argb;

    public Color(int r, int g, int b) { this(r, g, b, 255); }
    public Color(int r, int g, int b, int a) {
        if (((r | g | b | a) & ~0xff) != 0) throw new IllegalArgumentException("Color parameter outside of expected range");
        argb = a << 24 | r << 16 | g << 8 | b;
    }
    public Color(int rgb) { argb = 0xff000000 | rgb; }
    public Color(int argb, boolean hasAlpha) { this.argb = hasAlpha ? argb : 0xff000000 | argb; }
    public Color(float r, float g, float b) { this(r, g, b, 1f); }
    public Color(float r, float g, float b, float a) {
        this((int) (r * 255 + 0.5), (int) (g * 255 + 0.5), (int) (b * 255 + 0.5), (int) (a * 255 + 0.5));
    }

    public int getRed() { return argb >> 16 & 0xff; }
    public int getGreen() { return argb >> 8 & 0xff; }
    public int getBlue() { return argb & 0xff; }
    public int getAlpha() { return argb >>> 24; }
    public int getRGB() { return argb; }
    public int getTransparency() { return getAlpha() == 255 ? 1 : getAlpha() == 0 ? 2 : 3; }

    public Color brighter() {
        int r = getRed(), g = getGreen(), b = getBlue(), i = 3;
        if (r == 0 && g == 0 && b == 0) return new Color(i, i, i, getAlpha());
        if (r > 0 && r < i) r = i;
        if (g > 0 && g < i) g = i;
        if (b > 0 && b < i) b = i;
        return new Color(Math.min((int) (r / 0.7), 255), Math.min((int) (g / 0.7), 255), Math.min((int) (b / 0.7), 255), getAlpha());
    }
    public Color darker() {
        return new Color(Math.max((int) (getRed() * 0.7), 0), Math.max((int) (getGreen() * 0.7), 0),
                         Math.max((int) (getBlue() * 0.7), 0), getAlpha());
    }
    public static Color decode(String s) { return new Color(Integer.decode(s)); }
    public static Color getHSBColor(float h, float s, float b) { return new Color(HSBtoRGB(h, s, b)); }

    public static int HSBtoRGB(float hue, float saturation, float brightness) {
        int r = 0, g = 0, b = 0;
        if (saturation == 0) {
            r = g = b = (int) (brightness * 255.0f + 0.5f);
        } else {
            float h = (hue - (float) Math.floor(hue)) * 6.0f;
            float f = h - (float) Math.floor(h);
            float p = brightness * (1.0f - saturation);
            float q = brightness * (1.0f - saturation * f);
            float t = brightness * (1.0f - (saturation * (1.0f - f)));
            switch ((int) h) {
                case 0: r = (int) (brightness * 255 + .5f); g = (int) (t * 255 + .5f); b = (int) (p * 255 + .5f); break;
                case 1: r = (int) (q * 255 + .5f); g = (int) (brightness * 255 + .5f); b = (int) (p * 255 + .5f); break;
                case 2: r = (int) (p * 255 + .5f); g = (int) (brightness * 255 + .5f); b = (int) (t * 255 + .5f); break;
                case 3: r = (int) (p * 255 + .5f); g = (int) (q * 255 + .5f); b = (int) (brightness * 255 + .5f); break;
                case 4: r = (int) (t * 255 + .5f); g = (int) (p * 255 + .5f); b = (int) (brightness * 255 + .5f); break;
                case 5: r = (int) (brightness * 255 + .5f); g = (int) (p * 255 + .5f); b = (int) (q * 255 + .5f); break;
            }
        }
        return 0xff000000 | (r << 16) | (g << 8) | b;
    }

    public static float[] RGBtoHSB(int r, int g, int b, float[] hsb) {
        if (hsb == null) hsb = new float[3];
        int max = Math.max(r, Math.max(g, b)), min = Math.min(r, Math.min(g, b));
        float bright = max / 255f, sat = max != 0 ? (float) (max - min) / max : 0, hue;
        if (sat == 0) {
            hue = 0;
        } else {
            float rc = (float) (max - r) / (max - min), gc = (float) (max - g) / (max - min), bc = (float) (max - b) / (max - min);
            if (r == max) hue = bc - gc; else if (g == max) hue = 2.0f + rc - bc; else hue = 4.0f + gc - rc;
            hue /= 6.0f;
            if (hue < 0) hue += 1.0f;
        }
        hsb[0] = hue; hsb[1] = sat; hsb[2] = bright;
        return hsb;
    }

    @Override public boolean equals(Object o) { return o instanceof Color && ((Color) o).argb == argb; }
    @Override public int hashCode() { return argb; }
    @Override public String toString() { return getClass().getName() + "[r=" + getRed() + ",g=" + getGreen() + ",b=" + getBlue() + "]"; }
}
