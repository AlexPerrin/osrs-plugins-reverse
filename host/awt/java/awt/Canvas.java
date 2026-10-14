package java.awt;

/** Android shim of java.awt.Canvas: describes the game surface. */
public class Canvas {
    private int width, height;
    public int getWidth() { return width; }
    public int getHeight() { return height; }
    public Dimension getSize() { return new Dimension(width, height); }
    public void setSize(int w, int h) { width = w; height = h; }
}
