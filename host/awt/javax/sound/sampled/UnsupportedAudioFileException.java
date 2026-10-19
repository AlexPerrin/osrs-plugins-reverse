package javax.sound.sampled;

/** Android shim of javax.sound.sampled.UnsupportedAudioFileException. */
public class UnsupportedAudioFileException extends Exception {
    public UnsupportedAudioFileException() {}
    public UnsupportedAudioFileException(String message) { super(message); }
}
