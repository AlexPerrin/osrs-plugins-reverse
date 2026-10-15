package java.awt;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Android shim of java.awt.GraphicsEnvironment: a registry of named fonts. */
public abstract class GraphicsEnvironment {
    private static final Map<String, android.graphics.Typeface> registered = new ConcurrentHashMap<>();
    private static final GraphicsEnvironment INSTANCE = new GraphicsEnvironment() {};

    protected GraphicsEnvironment() {}
    public static GraphicsEnvironment getLocalGraphicsEnvironment() { return INSTANCE; }
    public static boolean isHeadless() { return false; }

    public boolean registerFont(Font font) {
        registered.put(font.getName(), font.getTypeface());
        return true;
    }

    public GraphicsDevice[] getScreenDevices() { return new GraphicsDevice[0]; }

    public String[] getAvailableFontFamilyNames() {
        String[] base = {Font.DIALOG, Font.SANS_SERIF, Font.SERIF, Font.MONOSPACED};
        String[] out = new String[base.length + registered.size()];
        System.arraycopy(base, 0, out, 0, base.length);
        int i = base.length;
        for (String n : registered.keySet()) out[i++] = n;
        return out;
    }

    static android.graphics.Typeface typefaceFor(String name) {
        android.graphics.Typeface t = registered.get(name);
        if (t != null) return t;
        if (Font.SERIF.equalsIgnoreCase(name)) return android.graphics.Typeface.SERIF;
        if (Font.MONOSPACED.equalsIgnoreCase(name) || Font.DIALOG_INPUT.equalsIgnoreCase(name)) return android.graphics.Typeface.MONOSPACE;
        return android.graphics.Typeface.SANS_SERIF;
    }
}
