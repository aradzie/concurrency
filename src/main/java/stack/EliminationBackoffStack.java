package stack;

import util.ThreadId;

import javax.annotation.Nonnull;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicReferenceArray;

/**
 * Concurrent stack as described in
 * <a href="http://www.cs.bgu.ac.il/~hendlerd/papers/scalable-stack.pdf">A
 * Scalable Lock-free Stack Algorithm</a> by Danny Hendler, Nir Shavit and Lena Yerushalmi.
 * <p/>
 * This algorithms relies on unique and stable thread ids that are used as
 * indices into the elimination array. These indices are obtained from
 * {@link ThreadId#id()}. This severely limits practical applicability of
 * this stack implementation.
 *
 * @param <T> Stack element type.
 * @see ThreadId
 */
public class EliminationBackoffStack<T> implements ConcurrentStack<T> {
    private static class Node<T> {
        final T value;
        volatile Node<T> next;

        Node(T value) {
            this.value = value;
        }

        boolean push(AtomicReference<Node<T>> refTop) {
            return refTop.compareAndSet(next = refTop.get(), this);
        }

        boolean pop(AtomicReference<Node<T>> refTop) {
            return refTop.compareAndSet(this, next);
        }
    }

    private enum Operation {
        PUSH,
        POP
    }

    private static class Cell<T> {
        final int id;
        final Operation operation;
        volatile Node<T> node;
        volatile boolean wakeup;

        Cell(Operation op, Node<T> n) {
            id = ThreadId.id() - 1;
            operation = op;
            node = n;
        }

        void reset() {
            wakeup = false;
        }

        int spin() {
            int x = 0;
            for (int n = 0; n < 10000; n++) {
                if (wakeup) {
                    break;
                }
                x = (x * 24049 + 11) % 7;
            }
            return x;
        }

        void wakeup() {
            wakeup = true;
        }
    }

    private static class EliminationArray<T> extends AtomicReferenceArray<Cell<T>> {
        private final Random random;

        EliminationArray(int length) {
            super(length);
            random = new Random();
        }

        /**
         * Compare-and-swap operation, unlike the standard compare-and-set
         * operation returns the current value instead of boolean flag.
         *
         * @param index Element index to swap atomically.
         * @param o     Expected value.
         * @param n     New value.
         * @return Current value which is expected value if succeeded,
         *         or something else if failed.
         */
        Cell<T> compareAndSwap(int index, Cell<T> o, Cell<T> n) {
            while (true) {
                Cell<T> v = get(index);
                if (v == o) {
                    if (compareAndSet(index, o, n)) {
                        return v;
                    }
                }
                else {
                    return v;
                }
            }
        }
    }

    private static final int THREADS = ThreadId.MAX;
    private final AtomicReference<Node<T>> top;
    private final EliminationArray<T> cells;

    public EliminationBackoffStack() {
        top = new AtomicReference<Node<T>>();
        cells = new EliminationArray<T>(THREADS);
    }

    @Override
    public void push(@Nonnull T e) {
        if (e == null) {
            throw new NullPointerException();
        }
        Node<T> node = new Node<T>(e);
        Cell<T> cell = null;
        while (true) {
            if (node.push(top)) {
                return;
            }
            if (cell == null) {
                cell = new Cell<T>(Operation.PUSH, node);
            }
            cell.reset();
            if (backoff(cell)) {
                return;
            }
        }
    }

    @Override
    public T peek() {
        Node<T> node = top.get();
        if (node == null) {
            return null;
        }
        return node.value;
    }

    @Override
    public T pop() {
        Cell<T> cell = null;
        while (true) {
            Node<T> node = top.get();
            if (node == null) {
                return null;
            }
            if (node.pop(top)) {
                return node.value;
            }
            if (cell == null) {
                cell = new Cell<T>(Operation.POP, null);
            }
            cell.reset();
            if (backoff(cell)) {
                return cell.node.value;
            }
        }
    }

    private boolean backoff(Cell<T> a) {
        backoff.incrementAndGet();
        cells.set(a.id, a);
        Cell<T> p = pickPartner(a);
        Cell<T> c;
        if (p != null && a.operation != p.operation) {
            c = cells.compareAndSwap(a.id, a, null);
            if (a != c) {
                return passiveCollide(a, c);
            }
            else {
                return activeCollide(a, p);
            }
        }
        a.spin();
        c = cells.compareAndSwap(a.id, a, null);
        if (a != c) {
            return passiveCollide(a, c);
        }
        return false;
    }

    private Cell<T> pickPartner(Cell<T> a) {
        while (true) {
            int id = cells.random.nextInt(cells.length());
            if (id != a.id) {
                Cell<T> p = cells.get(id);
                if (p != null && p.id == id) {
                    return p;
                }
                return null;
            }
        }
    }

    private boolean activeCollide(Cell<T> a, Cell<T> p) {
        if (cells.compareAndSet(p.id, p, a)) {
            if (a.operation == Operation.POP) {
                a.node = p.node;
                p.wakeup();
            }
            elimination.incrementAndGet();
            return true;
        }
        return false;
    }

    private boolean passiveCollide(Cell<T> p, Cell<T> a) {
        if (cells.compareAndSet(p.id, a, null)) {
            if (p.operation == Operation.POP) {
                p.node = a.node;
            }
            return true;
        }
        return false;
    }

    final AtomicInteger backoff = new AtomicInteger();
    final AtomicInteger elimination = new AtomicInteger();

    void dump() {
        System.out.println(String.format("backoffs:     %d", backoff.get()));
        System.out.println(String.format("eliminations: %d", elimination.get()));
    }
}
