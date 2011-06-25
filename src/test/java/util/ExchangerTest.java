package util;

import org.junit.Test;

import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertNull;

public class ExchangerTest {
    final Exchanger ex = new Exchanger.LockFree();

    @Test
    public void timeout()
            throws InterruptedException {
        assertNull(ex.exchange("hello", 1, TimeUnit.MILLISECONDS));
    }

    @Test(expected = InterruptedException.class)
    public void interrupt()
            throws InterruptedException {
        Thread.currentThread().interrupt();

        ex.exchange("hello", 1, TimeUnit.MILLISECONDS);
    }

    @Test
    public void concurrent()
            throws InterruptedException {
        Producer p1 = new Producer();
        Producer p2 = new Producer();
        Producer p3 = new Producer();

        p1.start();
        p2.start();
        p3.start();

        p1.join();
        p2.join();
        p3.join();
    }

    class Producer extends Thread {
        @Override
        public void run() {
            for (int n = 0; n < 10000; n++) {
                Integer v;
                try {
                    v = ex.exchange(n, 1, TimeUnit.MILLISECONDS);
                } catch (InterruptedException ex) {
                    return;
                }
                if (v == null) {
                    continue;
                }
                if (v > n) {
                    n = v;
                }
            }
        }
    }
}
