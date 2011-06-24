package casn;

import org.junit.Test;

import java.util.*;

import static org.junit.Assert.*;

public class LockFreeLinkedListTest {
    private static final Random R = new Random();

    @Test
    public void test() {
        LockFreeLinkedList<String> list = new LockFreeLinkedList<String>();
        assertFalse(list.listIterator().hasNext());
        assertFalse(list.listIterator().hasPrevious());
        assertEquals(0, list.size());
        list.add("uno");
        assertEquals(1, list.size());
        assertEquals("uno", list.get(0));
        assertTrue(list.contains("uno"));
        list.add("due");
        list.add("tre");
        assertEquals(3, list.size());
        assertEquals("due", list.get(1));
        assertEquals("tre", list.get(2));
        assertTrue(list.contains("due"));
        assertTrue(list.contains("tre"));
        assertEquals(0, list.indexOf("uno"));
        assertEquals(0, list.lastIndexOf("uno"));
        assertEquals(1, list.indexOf("due"));
        assertEquals(1, list.lastIndexOf("due"));
        assertEquals(2, list.indexOf("tre"));
        assertEquals(2, list.lastIndexOf("tre"));
        ListIterator<String> it = list.listIterator();
        assertTrue(it.hasNext());
        assertFalse(it.hasPrevious());
        assertEquals("uno", it.next());
        assertTrue(it.hasNext());
        assertTrue(it.hasPrevious());
        assertEquals("due", it.next());
        assertTrue(it.hasNext());
        assertTrue(it.hasPrevious());
        assertEquals("tre", it.next());
        assertFalse(it.hasNext());
        assertTrue(it.hasPrevious());
        assertEquals("due", it.previous());
        assertTrue(it.hasNext());
        assertTrue(it.hasPrevious());
        assertEquals("uno", it.previous());
        assertTrue(it.hasNext());
        assertFalse(it.hasPrevious());
        list.clear();
        assertFalse(list.listIterator().hasNext());
        assertFalse(list.listIterator().hasPrevious());
        assertEquals(0, list.size());
        list.add(0, "tre");
        list.add(0, "due");
        list.add(0, "uno");
        assertEquals("uno", list.get(0));
        assertEquals("due", list.get(1));
        assertEquals("tre", list.get(2));
        list.remove(2);
        list.remove(1);
        list.remove("uno");
        list.remove("unknown");
        assertEquals(0, list.size());
    }

    @Test
    public void threads() {
        final LockFreeLinkedList<String> list = new LockFreeLinkedList<String>();

        class Runner extends Thread {
            final Set<String> set = randomSet(1000);

            @Override
            public void run() {
                for (String s : set) {
                    list.add(s);
                }
            }
        }

        class Group extends ArrayList<Runner> {
            Group(int count) {
                for (int n = 0; n < count; n++) {
                    add(new Runner());
                }
            }

            void start() {
                for (Runner runner : this) {
                    runner.start();
                }
            }

            void join() {
                for (Runner runner : this) {
                    try {
                        runner.join();
                    } catch (InterruptedException ex) {
                    }
                }
            }

            Set<String> superSet() {
                HashSet<String> set = new HashSet<String>();
                for (Runner runner : this) {
                    set.addAll(runner.set);
                }
                return set;
            }
        }

        Group group = new Group(Runtime.getRuntime().availableProcessors());
        group.start();
        group.join();

        assertEquals(group.superSet().size(), list.size());
        for (Runner runner : group) {
            assertTrue(list.containsAll(runner.set));
        }
    }

    private static Set<String> randomSet(int size) {
        HashSet<String> set = new HashSet<String>();
        for (int n = 0; n < size; n++) {
            set.add(randomString(10));
        }
        return Collections.unmodifiableSet(set);
    }

    private static String randomString(int length) {
        char[] c = new char[length];
        for (int i = 0; i < c.length; i++) {
            c[i] = (char) ('a' + R.nextInt('z' - 'a') + 1);
        }
        return new String(c);
    }
}
