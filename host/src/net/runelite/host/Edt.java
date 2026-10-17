package net.runelite.host;

import android.os.SystemClock;
import android.util.Log;

/**
 * The Swing event-dispatch thread ("AWT-EventQueue-0"). Runs SwingUtilities.invokeLater work,
 * sidebar taps (Swing listeners) and the sidebar model build.
 *
 * Native game reads are only safe on the game thread, so the EDT and the game thread take turns:
 * once per cycle (Sidebar.onCycle, after the game's own cycle) the game thread hands its turn to
 * the EDT if it has work and waits until the EDT hands it back. While it holds the turn, the EDT
 * counts as the client thread natively (Native.setClientDelegate), so client getters called from
 * Swing code (Banked XP's combo listener reads skills) work as if run on the game thread, and the
 * two threads never run plugin code at the same time.
 *
 * A blocking JOptionPane on the EDT gives the turn back while the dialog is up (release/acquire
 * around the wait), so the game keeps cycling instead of disconnecting; the code after the dialog
 * resumes at the next cycle's handover.
 */
final class Edt {
    private static final String TAG = Host.TAG;
    /** A game-thread wait for the EDT longer than this is logged with the EDT's stack. */
    private static final long SLOW_MS = 3000;

    private static final Object lock = new Object();
    /** The EDT holds the turn (the game thread waits). */
    private static boolean edtTurn;
    /** The EDT waits for a turn (it has a task, or resumes after a dialog). */
    private static boolean edtWants;
    private static volatile Thread thread;
    /** The game thread has started handing over turns (the host is subscribed to cycles). */
    private static volatile boolean cycling;

    private Edt() {}

    static synchronized void start() {
        if (thread != null) return;
        Thread t = new Thread(Edt::loop, "AWT-EventQueue-0");
        t.setDaemon(true);
        thread = t;
        javax.swing.SwingUtilities.__edt = t;
        javax.swing.SwingUtilities.__invokeAndWait = Edt::invokeAndWait;
        t.start();
        Log.i(TAG, "EDT started");
    }

    static boolean running() { return thread != null; }

    static boolean isEdt() { return Thread.currentThread() == thread; }

    private static void loop() {
        while (true) {
            Runnable r;
            try {
                r = javax.swing.SwingUtilities.__queue.take();
            } catch (InterruptedException e) {
                continue;
            }
            acquire();
            try {
                run(r);
                // Drain what is queued now (and what these tasks queue), bounded so a task that
                // keeps re-posting itself can't hold the game.
                for (int i = 0; i < 1000 && (r = javax.swing.SwingUtilities.__queue.poll()) != null; i++) run(r);
            } finally {
                release();
            }
        }
    }

    private static void run(Runnable r) {
        try {
            r.run();
        } catch (Throwable t) {
            Log.e(TAG, "Swing task threw", t);
        }
    }

    /** EDT: waits until the game thread hands over its turn. */
    static void acquire() {
        synchronized (lock) {
            edtWants = true;
            while (!edtTurn) {
                try {
                    lock.wait();
                } catch (InterruptedException ignored) {
                }
            }
            edtWants = false;
        }
        Native.setClientDelegate(true);
    }

    /** EDT: gives the turn back to the game thread. */
    static void release() {
        Native.setClientDelegate(false);
        synchronized (lock) {
            edtTurn = false;
            lock.notifyAll();
        }
    }

    /**
     * Game thread, once per cycle: if the EDT has work, lets it run while the game waits here.
     * Without a started EDT, runs the Swing queue inline (the game thread acts as the EDT).
     */
    static void yieldToEdt() {
        if (thread == null) {
            for (int i = 0; i < 1000; i++) {
                Runnable r = javax.swing.SwingUtilities.__queue.poll();
                if (r == null) break;
                run(r);
            }
            return;
        }
        cycling = true;
        synchronized (lock) {
            if (!edtWants) return;
            edtTurn = true;
            lock.notifyAll();
            long t0 = SystemClock.uptimeMillis();
            boolean warned = false;
            while (edtTurn) {
                try {
                    lock.wait(500);
                } catch (InterruptedException ignored) {
                }
                if (!warned && edtTurn && SystemClock.uptimeMillis() - t0 > SLOW_MS) {
                    warned = true;
                    StringBuilder sb = new StringBuilder("game thread waiting on the EDT for " + SLOW_MS + " ms:");
                    StackTraceElement[] st = thread.getStackTrace();
                    for (int i = 0; i < Math.min(st.length, 16); i++) sb.append("\n  ").append(st[i]);
                    Log.w(TAG, sb.toString());
                }
            }
        }
    }

    /**
     * SwingUtilities.invokeAndWait from a thread other than the EDT. The game thread runs it
     * inline: the EDT can't be running then (they take turns), and waiting would stall the cycle.
     * So does the host's start thread before cycles reach the host (plugin startUp; no turns are
     * handed over yet, so nothing else runs Swing code). Other threads queue it and wait for it
     * (as Swing does), up to a minute.
     */
    private static void invokeAndWait(Runnable r) {
        if (Native.isClientThread() || !cycling) {
            run(r);
            return;
        }
        final java.util.concurrent.CountDownLatch done = new java.util.concurrent.CountDownLatch(1);
        javax.swing.SwingUtilities.__queue.add(() -> {
            try {
                r.run();
            } finally {
                done.countDown();
            }
        });
        try {
            if (!done.await(60, java.util.concurrent.TimeUnit.SECONDS)) Log.w(TAG, "invokeAndWait timed out on " + Thread.currentThread().getName());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
