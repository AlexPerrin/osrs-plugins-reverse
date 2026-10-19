package javax.sound.sampled;

/** Android shim of javax.sound.sampled.DataLine (no format, buffer or level queries). */
public interface DataLine extends Line {
    void drain();
    void flush();
    void start();
    void stop();
    boolean isRunning();
    boolean isActive();
    int getFramePosition();
    long getMicrosecondPosition();
}
