package casn;

import javax.annotation.Nullable;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Multi-location compare-and-set operation it can update multiple
 * refs in one atomic transaction. This is a generalization of the
 * {@link AtomicReference} operation to multiple values.
 * <p/>
 * This class implements algorithm described in the publication
 * <a href="http://research.microsoft.com/en-us/um/people/tharris/papers/2002-disc.pdf">A
 * Practical Multi-Word Compare-and-Swap Operation</a> by Timothy L. Harris, Keir Fraser
 * and Ian Pratt.
 */
public final class CASNRef<T> {
    /**
     * Forms chain of refs to update in a single atomic operation.
     */
    public static class Cell {
        /**
         * One of the atomic refs to update.
         */
        public final CASNRef ref;
        /**
         * Expected value.
         */
        public final Object o;
        /**
         * New value.
         */
        public final Object n;
        /**
         * Next cell in the list.
         */
        public final Cell next;

        public static <T> Cell cell(CASNRef<T> ref, @Nullable T o, @Nullable T n) {
            return new Cell(ref, o, n);
        }

        public static <T> Cell cell(CASNRef<T> ref, @Nullable T o, @Nullable T n, Cell next) {
            return new Cell(ref, o, n, next);
        }

        private Cell(CASNRef ref, Object o, Object n) {
            this.ref = ref;
            this.o = o;
            this.n = n;
            next = null;
        }

        private Cell(CASNRef ref, Object o, Object n, Cell next) {
            this.ref = ref;
            this.o = o;
            this.n = n;
            this.next = next;
        }
    }

    /**
     * Restricted double-compare single-swap helper.
     */
    private static class RDCSSDescriptor {
        final CASNRef ref1;
        final Object o1;
        final CASNRef ref2;
        final Object o2, n2;

        RDCSSDescriptor(CASNRef ref1, Object o1,
                        CASNRef ref2, Object o2, Object n2) {
            this.ref1 = ref1;
            this.o1 = o1;
            this.ref2 = ref2;
            this.o2 = o2;
            this.n2 = n2;
        }

        @SuppressWarnings({"unchecked"})
        Object update() {
            Object r;
            while (true) {
                r = ref2.compareAndSwap(o2, this);
                if (!(r instanceof RDCSSDescriptor)) {
                    break;
                }
                //noinspection CastToConcreteClass
                ((RDCSSDescriptor) r).complete();
            }
            if (r == o2) {
                complete();
            }
            return r;
        }

        @SuppressWarnings({"unchecked"})
        void complete() {
            Object v = ref1.ref.get();
            if (v == o1) {
                ref2.compareAndSwap(this, n2);
            }
            else {
                ref2.compareAndSwap(this, o2);
            }
        }
    }

    /**
     * CASN operation status.
     */
    private enum Status {
        /**
         * CASN is not started or in progress.
         */
        UNDECIDED,
        /**
         * CASN failed.
         */
        FAILED,
        /**
         * CASN succeeded.
         */
        SUCCEEDED
    }

    /**
     * Multi-location compare-and-swap helper.
     */
    private static class CASNDescriptor {
        final CASNRef<Status> status;
        final Cell list;

        CASNDescriptor(Cell l) {
            status = new CASNRef<Status>(Status.UNDECIDED);
            list = l;
        }

        @SuppressWarnings({"unchecked"})
        boolean casnUpdate() {
            // Phase 1
            if (status.ref.get() == Status.UNDECIDED) {
                Status s = Status.SUCCEEDED;
                for (Cell cell = list; cell != null; cell = cell.next) {
                    RDCSSDescriptor d = new RDCSSDescriptor(
                            status, Status.UNDECIDED, cell.ref, cell.o, this);
                    while (true) {
                        Object r = d.update();
                        if (r instanceof CASNDescriptor) {
                            if (r != this) {
                                //noinspection CastToConcreteClass
                                ((CASNDescriptor) r).casnUpdate();
                                continue;
                            }
                        }
                        else if (r != cell.o) {
                            s = Status.FAILED;
                        }
                        break;
                    }
                    if (s != Status.SUCCEEDED) {
                        break;
                    }
                }
                status.compareAndSwap(Status.UNDECIDED, s);
            }
            // Phase 2
            if (status.ref.get() == Status.SUCCEEDED) {
                for (Cell cell = list; cell != null; cell = cell.next) {
                    cell.ref.compareAndSwap(this, cell.n);
                }
                return true;
            }
            else {
                for (Cell cell = list; cell != null; cell = cell.next) {
                    cell.ref.compareAndSwap(this, cell.o);
                }
                return false;
            }
        }
    }

    private final AtomicReference<T> ref;

    public CASNRef() {
        ref = new AtomicReference<T>();
    }

    public CASNRef(@Nullable T v) {
        ref = new AtomicReference<T>(v);
    }

    /**
     * Update ref value atomically.
     *
     * @param o Expected value.
     * @param n New value.
     * @return Update status.
     */
    public boolean cas(@Nullable T o, @Nullable T n) {
        return new CASNDescriptor(new Cell(this, o, n)).casnUpdate();
    }

    /**
     * Update multiple refs atomically.
     *
     * @param cell List of refs with expected and new values
     *             to apply all at once atomically.
     * @return Update status.
     */
    public static boolean casn(Cell cell) {
        return new CASNDescriptor(cell).casnUpdate();
    }

    /**
     * @param value The new value to set to the ref.
     */
    public void set(@Nullable T value) {
        while (true) {
            T o = get();
            T v = compareAndSwap(o, value);
            if (o == v) {
                break;
            }
        }
    }

    /**
     * @return The current ref value.
     */
    @SuppressWarnings({"unchecked"})
    public T get() {
        return (T) ncasGet();
    }

    /**
     * Do not confuse this compare-and-swap operation with the standard
     * compare-and-set operation. The former returns the current value,
     * while the later returns boolean flag.
     * <pre><code>
     * Object expected = ...
     * Object next = ...
     * Object current;
     * if ((current = casImpl(expected, next)) == expected) {
     *     // succeeded
     * } else {
     *     // failed, examine current value
     * }
     * </code></pre>
     *
     * @param o Expected value.
     * @param n New value.
     * @return Current value which is expected value if succeeded,
     *         or something else if failed.
     */
    private T compareAndSwap(T o, T n) {
        while (true) {
            T v = ref.get();
            if (o == v) {
                if (ref.compareAndSet(o, n)) {
                    return o;
                }
            }
            else {
                return v;
            }
        }
    }

    private Object ncasGet() {
        Object v;
        while (true) {
            v = rdcssGet();
            if (!(v instanceof CASNDescriptor)) {
                break;
            }
            //noinspection CastToConcreteClass
            ((CASNDescriptor) v).casnUpdate();
        }
        return v;
    }

    private Object rdcssGet() {
        Object v;
        while (true) {
            v = ref.get();
            if (!(v instanceof RDCSSDescriptor)) {
                break;
            }
            //noinspection CastToConcreteClass
            ((RDCSSDescriptor) v).complete();
        }
        return v;
    }
}
