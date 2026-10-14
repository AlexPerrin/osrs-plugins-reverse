package net.runelite.client.callback;

import android.util.Log;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.BooleanSupplier;

/**
 * Replaces RuneLite's ClientThread (same name and API) on top of the doCycle hook: queued
 * work runs on the game thread after each client cycle; a BooleanSupplier that returns false
 * is retried on the next cycle.
 */
public class ClientThread {
    private final ConcurrentLinkedQueue<BooleanSupplier> queue = new ConcurrentLinkedQueue<>();
    private final ConcurrentLinkedQueue<Runnable> tickEnd = new ConcurrentLinkedQueue<>();
    private volatile Thread thread;

    public void invoke(Runnable r) {
        invoke(() -> {
            r.run();
            return true;
        });
    }

    public void invoke(BooleanSupplier r) {
        if (Thread.currentThread() == thread) {
            if (!run(r)) queue.add(r);
        } else {
            queue.add(r);
        }
    }

    public void invokeLater(Runnable r) {
        queue.add(() -> {
            r.run();
            return true;
        });
    }

    public void invokeLater(BooleanSupplier r) {
        queue.add(r);
    }

    public void invokeAtTickEnd(Runnable r) {
        tickEnd.add(r);
    }

    /** Host: called on the game thread after every client cycle. */
    public void runQueued() {
        thread = Thread.currentThread();
        List<BooleanSupplier> retry = new ArrayList<>();
        for (int n = queue.size(); n > 0; n--) {
            BooleanSupplier r = queue.poll();
            if (r == null) break;
            if (!run(r)) retry.add(r);
        }
        queue.addAll(retry);
    }

    /** Host: called on the game thread after GameTick subscribers. */
    public void runTickEnd() {
        for (Runnable r; (r = tickEnd.poll()) != null; ) {
            Runnable task = r;
            run(() -> {
                task.run();
                return true;
            });
        }
    }

    private static boolean run(BooleanSupplier r) {
        try {
            return r.getAsBoolean();
        } catch (Throwable t) {
            Log.e("osrsrl-host", "client thread task threw", t);
            return true;
        }
    }

    /** Host: true on the game thread. */
    public boolean isClientThread() {
        return Thread.currentThread() == thread;
    }
}
