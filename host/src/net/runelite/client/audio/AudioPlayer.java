package net.runelite.client.audio;

import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.util.Log;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.OpenOption;
import javax.inject.Singleton;
import net.runelite.client.util.Filepath;

/**
 * Replaces RuneLite's AudioPlayer (same name; the original plays through javax.sound.sampled,
 * which Android lacks). Each call plays one clip through android.media.MediaPlayer, with the
 * gain in dB turned into a linear volume. Streams and resources are copied to a temp file first
 * (MediaPlayer reads files or descriptors). The original declares javax.sound exceptions; callers
 * only catch them, so IOException covers the failures here.
 */
@Singleton
public class AudioPlayer {
    private static final String TAG = "osrsrl-host";
    /** Players until they complete: an unreferenced MediaPlayer is finalized (and stopped) mid-clip. */
    private static final java.util.Set<MediaPlayer> PLAYING = java.util.Collections.synchronizedSet(new java.util.HashSet<>());

    public void play(File file, float gain) throws IOException {
        start(file, gain, false);
    }

    public void play(Filepath file, float gain) throws IOException {
        try (InputStream in = file.openInputStream(new OpenOption[0])) {
            play(in, gain);
        }
    }

    public void play(Class<?> c, String path, float gain) throws IOException {
        try (InputStream in = c.getResourceAsStream(path)) {
            if (in == null) throw new IOException("resource not found: " + path);
            play(in, gain);
        }
    }

    public void play(InputStream stream, float gain) throws IOException {
        File tmp = File.createTempFile("rl-audio", null);
        try (OutputStream out = new FileOutputStream(tmp)) {
            byte[] buf = new byte[8192];
            for (int n; (n = stream.read(buf)) > 0; ) out.write(buf, 0, n);
        }
        start(tmp, gain, true);
    }

    private static void start(File file, float gain, boolean deleteAfter) throws IOException {
        float volume = (float) Math.min(1.0, Math.pow(10, gain / 20.0));
        MediaPlayer mp = new MediaPlayer();
        try {
            mp.setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build());
            mp.setDataSource(file.getAbsolutePath());
            mp.setVolume(volume, volume);
            mp.setOnCompletionListener(p -> {
                PLAYING.remove(p);
                p.release();
                if (deleteAfter) file.delete();
            });
            mp.setOnErrorListener((p, what, extra) -> {
                PLAYING.remove(p);
                p.release();
                if (deleteAfter) file.delete();
                return true;
            });
            mp.prepare();
            PLAYING.add(mp);
            mp.start();
            Log.i(TAG, "audio: " + file.getName() + " volume " + volume);  // unverified: audible on the device
        } catch (IOException | RuntimeException e) {
            PLAYING.remove(mp);
            mp.release();
            if (deleteAfter) file.delete();
            throw e instanceof IOException ? (IOException) e : new IOException(e);
        }
    }
}
