package javax.imageio;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;

/** Android shim of javax.imageio.ImageIO: decodes with BitmapFactory, encodes PNG/JPEG. */
public final class ImageIO {
    private ImageIO() {}

    public static void setUseCache(boolean useCache) {}

    public static BufferedImage read(InputStream in) throws IOException {
        if (in == null) throw new IllegalArgumentException("input == null!");
        Bitmap b = BitmapFactory.decodeStream(in);
        return b == null ? null : BufferedImage.fromBitmap(b);
    }

    public static BufferedImage read(URL url) throws IOException {
        if (url == null) throw new IllegalArgumentException("input == null!");
        try (InputStream in = url.openStream()) {
            return read(in);
        }
    }

    public static BufferedImage read(File f) throws IOException {
        try (InputStream in = new FileInputStream(f)) {
            return read(in);
        }
    }

    public static boolean write(java.awt.image.RenderedImage img, String format, OutputStream out) throws IOException {
        if (!(img instanceof BufferedImage)) return false;
        Bitmap.CompressFormat f = format.equalsIgnoreCase("jpg") || format.equalsIgnoreCase("jpeg")
                ? Bitmap.CompressFormat.JPEG : Bitmap.CompressFormat.PNG;
        return ((BufferedImage) img).toBitmap().compress(f, 95, out);
    }

    public static boolean write(java.awt.image.RenderedImage img, String format, File f) throws IOException {
        try (OutputStream out = new FileOutputStream(f)) {
            return write(img, format, out);
        }
    }
}
