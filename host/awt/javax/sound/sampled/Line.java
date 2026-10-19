package javax.sound.sampled;

/** Android shim of javax.sound.sampled.Line (no Line.Info or listeners). */
public interface Line extends AutoCloseable {
    void open() throws LineUnavailableException;
    @Override void close();
    boolean isOpen();
    Control[] getControls();
    boolean isControlSupported(Control.Type control);
    Control getControl(Control.Type control);
}
