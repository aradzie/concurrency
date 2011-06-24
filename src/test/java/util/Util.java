package util;

import java.util.*;

public class Util {
    public static final Random R = new Random();

    public static List<String> randomList(int size) {
        ArrayList<String> c = new ArrayList<String>(size);
        for (int n = 0; n < size; n++) {
            c.add(randomString(10));
        }
        return Collections.unmodifiableList(c);
    }

    public static Set<String> randomSet(int size) {
        HashSet<String> c = new HashSet<String>(size);
        for (int n = 0; n < size; n++) {
            c.add(randomString(10));
        }
        return Collections.unmodifiableSet(c);
    }

    public static String randomString(int length) {
        char[] c = new char[length];
        for (int i = 0; i < c.length; i++) {
            c[i] = (char) ('a' + R.nextInt('z' - 'a') + 1);
        }
        return new String(c);
    }
}
