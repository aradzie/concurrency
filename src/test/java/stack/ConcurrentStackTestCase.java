package stack;

import org.junit.Test;
import util.Util;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.*;

public abstract class ConcurrentStackTestCase {
    private static final String SEQUENTIAL = "sequential";
    private static final String CONCURRENT = "concurrent";

    protected abstract <T> ConcurrentStack<T> create();

    @Test
    public void sequential() {
        ConcurrentStack<String> s = create();
        assertNull(s.peek());
        assertNull(s.pop());
        s.push("1");
        s.push("2");
        s.push("3");
        assertEquals("3", s.peek());
        assertEquals("3", s.pop());
        assertEquals("2", s.peek());
        assertEquals("2", s.pop());
        assertEquals("1", s.peek());
        assertEquals("1", s.pop());
        assertNull(s.peek());
        assertNull(s.pop());
    }

    @Test
    public void concurrent()
            throws InterruptedException {
        List<String> a = Util.randomList(10000);
        List<String> b = new ArrayList<String>();

        ConcurrentStack<String> s = create();
        Producer<String> tp = new Producer<String>(s, a);
        Consumer<String> tc = new Consumer<String>(s, b);

        tp.start();
        tc.start();

        tp.join();
        tc.join();

        assertEquals(a.size(), b.size());
        assertTrue(a.containsAll(b));
        assertTrue(b.containsAll(a));
    }

    @Test
    public void perfSequential() {
        // Warm up
        {
            ConcurrentStack<Integer> stack = create();

            Benchmark benchmark = new Benchmark(SEQUENTIAL, stack);
            benchmark.run();
        }

        // Run
        {
            ConcurrentStack<Integer> stack = create();

            Benchmark benchmark = new Benchmark(SEQUENTIAL, stack);
            benchmark.run();
            benchmark.report();
        }
    }

    @Test
    public void perfConcurrent() {
        // Warm up
        {
            ConcurrentStack<Integer> stack = create();

            LoaderGroup group = new LoaderGroup(stack,
                    Runtime.getRuntime().availableProcessors());

            group.start();
            group.join();
        }

        // Run
        {
            ConcurrentStack<Integer> stack = create();

            LoaderGroup group = new LoaderGroup(stack,
                    Runtime.getRuntime().availableProcessors());

            group.start();
            group.join();

            group.report();
        }
    }

    final AtomicInteger counter = new AtomicInteger();

    class Group<T extends Thread> extends ArrayList<T> {
        void start() {
            for (T runner : this) {
                runner.start();
            }
        }

        void join() {
            for (T runner : this) {
                try {
                    runner.join();
                } catch (InterruptedException ex) {
                }
            }
        }
    }

    class Producer<T> extends Thread {
        final ConcurrentStack<T> stack;
        final Collection<T> items;

        Producer(ConcurrentStack<T> stack, Collection<T> items) {
            this.stack = stack;
            this.items = items;

            counter.incrementAndGet();
        }

        @Override
        public void run() {
            for (T item : items) {
                stack.push(item);
            }

            counter.decrementAndGet();
        }
    }

    class Consumer<T> extends Thread {
        final ConcurrentStack<T> stack;
        final Collection<T> items;

        Consumer(ConcurrentStack<T> stack, Collection<T> items) {
            this.stack = stack;
            this.items = items;
        }

        @Override
        public void run() {
            while (true) {
                T t = stack.pop();
                if (t == null) {
                    if (counter.get() == 0) {
                        break;
                    }
                }
                else {
                    items.add(t);
                }
            }
        }
    }

    class Benchmark {
        final String name;
        final ConcurrentStack<Integer> stack;
        volatile int count;
        volatile int time;

        Benchmark(String name, ConcurrentStack<Integer> stack) {
            this.name = name;
            this.stack = stack;
        }

        void run() {
            int c = 0;
            long s = System.currentTimeMillis();

            while (true) {
                int steps = 10;

                for (int n = 0; n < steps; n++) {
                    stack.push(c + n);
                    stack.pop();
                }

                c += steps;
                long f = System.currentTimeMillis();

                if (f - s > 1000) {
                    count = c;
                    time = (int) (f - s);
                    return;
                }
            }
        }

        void report() {
            int p = count / time;
            System.out.println(String.format("%s: %s: %d push-pop/msec",
                    name, stack.getClass().getSimpleName(), p));
        }
    }

    class LoaderGroup extends Group<Loader> {
        final ConcurrentStack<Integer> stack;

        LoaderGroup(ConcurrentStack<Integer> s, int c) {
            stack = s;
            for (int n = 0; n < c; n++) {
                add(new Loader(stack));
            }
        }

        void report() {
            for (Loader loader : this) {
                loader.benchmark.report();
            }
        }
    }

    class Loader extends Thread {
        final Benchmark benchmark;

        Loader(ConcurrentStack<Integer> stack) {
            benchmark = new Benchmark(CONCURRENT, stack);
        }

        @Override
        public void run() {
            benchmark.run();
        }
    }
}
