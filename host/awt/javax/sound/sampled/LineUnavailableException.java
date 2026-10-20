package javax.sound.sampled;

/** Android shim of javax.sound.sampled.LineUnavailableException. */
public class LineUnavailableException extends Exception {
    public LineUnavailableException() {}
    public LineUnavailableException(String message) { super(message); }
}
