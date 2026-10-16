package java.awt.image;

/** The shim's raster: the ARGB int buffer of a BufferedImage (getRaster shares it, copyData copies it). */
class HostRaster extends WritableRaster {
    final int width, height;
    final int[] pixels;

    HostRaster(int width, int height, int[] pixels) {
        this.width = width;
        this.height = height;
        this.pixels = pixels;
    }
}
