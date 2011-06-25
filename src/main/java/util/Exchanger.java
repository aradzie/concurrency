package util;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicStampedReference;

public interface Exchanger {
    @Nullable
    <T> T exchange(@Nonnull T v, long timeout, TimeUnit unit)
            throws InterruptedException;

    /**
     * A naive lock-free exchanger implementation. For a more robust exchanger,
     * see {@link java.util.concurrent.Exchanger Java Exchanger} class.
     */
    class LockFree implements Exchanger {
        // As two threads exchange their values they transition internal state machine
        // through the following sequence of states:
        // Thread A:  EMPTY -> WAITING -> ...    (waiting)    ... BUSY -> EMPTY -> return
        // Thread B:                          WAITING -> BUSY -> return
        private static final int EMPTY = 0;
        private static final int WAITING = 1;
        private static final int BUSY = 2;
        private final AtomicStampedReference<Object> ref;

        public LockFree() {
            ref = new AtomicStampedReference<Object>(null, EMPTY);
        }

        @Nullable
        @Override
        public <T> T exchange(@Nonnull T our, long timeout, TimeUnit unit)
                throws InterruptedException {
            if (our == null) {
                throw new NullPointerException();
            }
            int[] stampHolder = new int[1];
            long deadline = deadline(timeout, unit);
            while (true) {
                if (Thread.interrupted()) {
                    throw new InterruptedException();
                }
                Object their = ref.get(stampHolder);
                switch (stampHolder[0]) {
                    case EMPTY:
                        // The current value is empty, propose our value.
                        if (ref.compareAndSet(their, our, EMPTY, WAITING)) {
                            // Wait for encounter with other thread.
                            while (true) {
                                if (Thread.interrupted()) {
                                    throw new InterruptedException();
                                }
                                their = ref.get(stampHolder);
                                if (stampHolder[0] == BUSY) {
                                    ref.set(null, EMPTY);
                                    return (T) their;
                                }
                                if (isTimedOut(deadline)) {
                                    return null;
                                }
                            }
                        }
                        break;
                    case WAITING:
                        // Other thread has proposed its value, exchange.
                        if (ref.compareAndSet(their, our, WAITING, BUSY)) {
                            return (T) their;
                        }
                        break;
                }
                if (isTimedOut(deadline)) {
                    return null;
                }
            }
        }

        private long deadline(long timeout, TimeUnit unit) {
            if (timeout > 0) {
                return System.nanoTime() + unit.toNanos(timeout);
            }
            return 0;
        }

        private boolean isTimedOut(long deadline) {
            if (deadline > 0) {
                return System.nanoTime() > deadline;
            }
            return false;
        }
    }
}
