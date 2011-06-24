package util;

import java.util.Random;

/**
 * In case of contention it makes one of the threads step back
 * to give other threads a change to complete their transactions.
 */
public final class Backoff {
    public static final int PROCESSORS
            = Runtime.getRuntime().availableProcessors();
    private static final Random R = new Random();
    private final int minDelay, maxDelay;
    private int limit;

    public Backoff(int min, int max) {
        minDelay = min;
        maxDelay = max;
        limit = minDelay;
    }

    public void backoff() {
        int delay = R.nextInt(limit);
        if (limit < maxDelay) {
            limit = 2 * limit;
        }
        if (PROCESSORS > 1) {
            // Busy waiting on multiprocessor systems.
            // I think this algorithm requires fine tuning.
            busyWait(delay * 10000);
        }
        else {
            // Give another thread chance to run.
            // I have no idea how good this strategy is.
            Thread.yield();
        }
    }

    private int busyWait(int steps) {
        int x = 0;
        for (int n = 0; n < steps; n++) {
            x = (x * 24049 + 11) % 7;
        }
        return x;
    }
}
