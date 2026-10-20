package javax.sound.sampled;

import android.util.Log;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

/**
 * The host's Clip: open() keeps the audio file's bytes; start()/loop(n) play them once through
 * AudioPlayer (MediaPlayer) at the MASTER_GAIN value. Not modelled: frame positions, loop counts
 * above 0 (played once), stop() of a playing clip (MediaPlayer finishes it).
 */
final class HostClip implements Clip {
    private static final String TAG = "osrsrl-host";
    private final FloatControl gain = new FloatControl(FloatControl.Type.MASTER_GAIN, -80f, 6.0206f, 0.0625f, -1, 0f, "dB") {};
    private byte[] data;

    @Override public void open(AudioInputStream stream) throws LineUnavailableException, IOException {
        if (data != null) throw new IllegalStateException("Clip is already open");
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        for (int n; (n = stream.read(buf)) > 0; ) out.write(buf, 0, n);
        data = out.toByteArray();
    }

    @Override public void open() throws LineUnavailableException {
        throw new IllegalArgumentException("Clip.open() needs audio data: use open(AudioInputStream)");
    }

    @Override public void close() { data = null; }
    @Override public boolean isOpen() { return data != null; }
    @Override public Control[] getControls() { return new Control[] {gain}; }
    @Override public boolean isControlSupported(Control.Type control) { return control == FloatControl.Type.MASTER_GAIN; }
    @Override public Control getControl(Control.Type control) {
        if (control != FloatControl.Type.MASTER_GAIN) throw new IllegalArgumentException("Unsupported control type: " + control);
        return gain;
    }

    @Override public void start() {
        byte[] d = data;
        if (d == null) return;
        try {
            new net.runelite.client.audio.AudioPlayer().play(new ByteArrayInputStream(d), gain.getValue());
        } catch (IOException | RuntimeException e) {
            Log.w(TAG, "clip playback failed", e);
        }
    }

    @Override public void loop(int count) {
        if (count != 0) Log.i(TAG, "Clip.loop(" + count + "): played once");
        start();
    }

    @Override public void stop() {}
    @Override public void drain() {}
    @Override public void flush() {}
    @Override public boolean isRunning() { return false; }
    @Override public boolean isActive() { return false; }
    @Override public int getFramePosition() { return 0; }
    @Override public long getMicrosecondPosition() { return 0; }
    @Override public int getFrameLength() { return -1; }
    @Override public long getMicrosecondLength() { return -1; }
    @Override public void setFramePosition(int frames) {}
    @Override public void setMicrosecondPosition(long microseconds) {}
    @Override public void setLoopPoints(int start, int end) {}
}
