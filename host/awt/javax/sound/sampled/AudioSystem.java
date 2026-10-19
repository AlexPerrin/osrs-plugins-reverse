package javax.sound.sampled;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * Android shim of javax.sound.sampled.AudioSystem: clips play through the host's AudioPlayer
 * (android.media.MediaPlayer), like RuneLite's own sounds on the host. Streams are passed through
 * undecoded; MediaPlayer decodes the file when the clip starts.
 */
public class AudioSystem {
    private AudioSystem() {}

    public static Clip getClip() throws LineUnavailableException { return new HostClip(); }

    public static AudioInputStream getAudioInputStream(InputStream stream) throws UnsupportedAudioFileException, IOException {
        if (stream == null) throw new NullPointerException("stream");
        return new AudioInputStream(stream);
    }

    public static AudioInputStream getAudioInputStream(File file) throws UnsupportedAudioFileException, IOException {
        return new AudioInputStream(new BufferedInputStream(new FileInputStream(file)));
    }
}
