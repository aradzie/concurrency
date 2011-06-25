package casn;

import org.junit.Test;

import static casn.CASNRef.Cell.cell;
import static org.junit.Assert.*;

public class CASNRefTest {
    @Test
    public void nullValue() {
        CASNRef<String> r = new CASNRef<String>(null);
        assertNull(r.get());
        r.set(null);
        assertNull(r.get());
        assertTrue(r.cas(null, null));
        assertNull(r.get());
        assertTrue(r.cas(null, "null"));
        assertNotNull(r.get());
        assertTrue(r.cas("null", null));
        assertNull(r.get());
        r.set("null");
        assertEquals("null", r.get());
        r.set(null);
        assertNull(r.get());
    }

    @Test
    public void cas1() {
        CASNRef<String> r = new CASNRef<String>("uno");
        assertEquals("uno", r.get());
        assertTrue(r.cas("uno", "due"));
        assertEquals("due", r.get());
        assertFalse(r.cas("uno", "tre"));
        assertEquals("due", r.get());
        assertTrue(r.cas("due", "tre"));
        assertEquals("tre", r.get());
        assertTrue(r.cas("tre", "tre"));
        assertEquals("tre", r.get());
        r.set("null");
        assertEquals("null", r.get());
    }

    @Test
    public void casN() {
        CASNRef<String> r1 = new CASNRef<String>("v1");
        CASNRef<String> r2 = new CASNRef<String>("v2");
        CASNRef<String> r3 = new CASNRef<String>("v3");
        assertTrue(CASNRef.casn(
                cell(r1, "v1", "v1'",
                        cell(r2, "v2", "v2'",
                                cell(r3, "v3", "v3'")))));
        assertEquals("v1'", r1.get());
        assertEquals("v2'", r2.get());
        assertEquals("v3'", r3.get());
        assertFalse(CASNRef.casn(
                cell(r1, "v1", "v1'",
                        cell(r2, "v2", "v2'",
                                cell(r3, "v3", "v3'")))));
        assertEquals("v1'", r1.get());
        assertEquals("v2'", r2.get());
        assertEquals("v3'", r3.get());
        assertTrue(CASNRef.casn(
                cell(r1, "v1'", "v1",
                        cell(r2, "v2'", "v2",
                                cell(r3, "v3'", "v3")))));
        assertEquals("v1", r1.get());
        assertEquals("v2", r2.get());
        assertEquals("v3", r3.get());
        assertTrue(CASNRef.casn(
                cell(r1, "v1", "v1",
                        cell(r2, "v2", "v2",
                                cell(r3, "v3", "v3")))));
        assertEquals("v1", r1.get());
        assertEquals("v2", r2.get());
        assertEquals("v3", r3.get());
    }

    @Test
    public void threads()
            throws Exception {
        final CASNRef<String> r1 = new CASNRef<String>("v1");
        final CASNRef<String> r2 = new CASNRef<String>("v2");
        final CASNRef<String> r3 = new CASNRef<String>("v3");

        class Runner extends Thread {
            private String v1;
            private String v2;
            private String v3;
            private boolean casn;

            @Override
            public void run() {
                for (int n = 0; n < 10000; n++) {
                    v1 = r1.get();
                    v2 = r2.get();
                    v3 = r3.get();
                    casn = CASNRef.casn(
                            cell(r1, v1, v1 + "*",
                                    cell(r2, v2, v2 + "*",
                                            cell(r3, v3, v3 + "*"))));
                }
            }
        }

        Runner t1 = new Runner();
        Runner t2 = new Runner();

        t1.start();
        t2.start();

        t1.join();
        t2.join();
    }
}
