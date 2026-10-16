package net.runelite.host;

import android.graphics.Bitmap;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.util.function.Consumer;
import net.runelite.client.ui.DrawManager;

/**
 * RuneLite's DrawManager fed with real frames: a next-frame listener (screenshots) asks the native
 * side to read the next finished frame (frame_capture.cpp, glReadPixels on the render thread); a
 * later client cycle hands it, scaled to the canvas so it matches canvas coordinates, to the
 * listeners. Every-frame listeners run each client cycle.
 */
public final class HostDrawManager extends DrawManager {
    private volatile boolean pending;
    private int waitedCycles;

    @Override
    public void requestNextFrameListener(Consumer<Image> listener) {
        synchronized (this) {
            pending = true;
            super.requestNextFrameListener(listener);
        }
        Native.requestFrame();
    }

    /** Game thread, each client cycle. */
    void onCycle() {
        synchronized (this) {
            // Every-frame listeners only: with no next-frame listener queued the supplier isn't used
            // (a null image would drop queued listeners, hence the lock with requestNextFrameListener).
            if (!pending) {
                processDrawComplete(() -> null);
                return;
            }
        }
        int[] f = Native.takeFrame();
        if (f == null) {
            if (++waitedCycles % 50 == 0) Native.requestFrame();  // a lost request (no swap happened)
            return;
        }
        waitedCycles = 0;
        pending = false;
        final int w = f[0], h = f[1];
        processDrawComplete(() -> {
            Bitmap b = Bitmap.createBitmap(f, 2, w, w, h, Bitmap.Config.ARGB_8888);
            java.awt.Dimension c = OverlayRenderer.canvasSize();
            if (c.width > 0 && c.height > 0 && (c.width != w || c.height != h)) b = Bitmap.createScaledBitmap(b, c.width, c.height, true);
            return BufferedImage.fromBitmap(b);
        });
    }
}
