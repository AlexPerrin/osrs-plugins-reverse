package javax.sound.sampled;

import java.io.IOException;

/** Android shim of javax.sound.sampled.Clip. */
public interface Clip extends DataLine {
    int LOOP_CONTINUOUSLY = -1;

    void open(AudioInputStream stream) throws LineUnavailableException, IOException;
    int getFrameLength();
    long getMicrosecondLength();
    void setFramePosition(int frames);
    void setMicrosecondPosition(long microseconds);
    void setLoopPoints(int start, int end);
    void loop(int count);
}
