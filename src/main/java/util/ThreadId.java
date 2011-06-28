package util;

import java.util.concurrent.atomic.AtomicInteger;

public class ThreadId {
    public static final int MAX = Runtime.getRuntime().availableProcessors();
    private static final AtomicInteger counter = new AtomicInteger();
    private static final ThreadLocal<Integer> id = new ThreadLocal<Integer>() {
        @Override
        protected Integer initialValue() {
            int id = counter.incrementAndGet();
            if (id > MAX) {
                throw new IllegalStateException();
            }
            return id;
        }
    };

    public static void reset() {
        counter.set(0);
    }

    /**
     * @return One-based continuous id for the current thread.
     */
    public static int id() {
        return id.get();
    }
}
