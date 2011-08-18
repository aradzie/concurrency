package stack;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.concurrent.locks.ReentrantLock;

/**
 * The simplest concurrent stack interface ever possible.
 *
 * @param <T> Stack element type.
 */
public interface ConcurrentStack<T> {
    void push(@Nonnull T e);

    /**
     * @return Top element of the stack without removing it,
     *         or <code>null</code> if stack is empty.
     */
    @Nullable
    T peek();

    /**
     * @return Top element of the stack after removing it,
     *         or <code>null</code> if stack is empty.
     */
    @Nullable
    T pop();

    /**
     * Standard java stack decorated by synchronizing wrapper.
     *
     * @param <T> Stack element type.
     */
    class Blocking<T> implements ConcurrentStack<T> {
        private final ReentrantLock lock = new ReentrantLock();
        private final ArrayList<T> list = new ArrayList<T>();

        @Override
        public void push(@Nonnull T e) {
            if (e == null) {
                throw new NullPointerException();
            }
            lock.lock();
            try {
                list.add(e);
            }
            finally {
                lock.unlock();
            }
        }

        @Override
        public T peek() {
            lock.lock();
            try {
                if (list.isEmpty()) {
                    return null;
                }
                return list.get(list.size() - 1);
            }
            finally {
                lock.unlock();
            }
        }

        @Override
        public T pop() {
            lock.lock();
            try {
                if (list.isEmpty()) {
                    return null;
                }
                return list.remove(list.size() - 1);
            }
            finally {
                lock.unlock();
            }
        }
    }
}
