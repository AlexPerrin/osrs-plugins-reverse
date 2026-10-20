package javax.sound.sampled;

import java.io.FilterInputStream;
import java.io.InputStream;

/**
 * Android shim of javax.sound.sampled.AudioInputStream: the encoded audio file's bytes, undecoded
 * (the host's clips hand the whole file to android.media.MediaPlayer). No AudioFormat.
 */
public class AudioInputStream extends FilterInputStream {
    AudioInputStream(InputStream in) { super(in); }
}
