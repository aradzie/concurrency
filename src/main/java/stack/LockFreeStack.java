package stack;

import javax.annotation.Nonnull;
import java.util.concurrent.atomic.AtomicReference;

public class LockFreeStack<T> implements ConcurrentStack<T> {
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

    private final AtomicReference<Node<T>> top;

    public LockFreeStack() {
        top = new AtomicReference<Node<T>>();
    }

    @Override
    public void push(@Nonnull T e) {
        if (e == null) {
            throw new NullPointerException();
        }
        Node<T> node = new Node<T>(e);
        while (true) {
            if (node.push(top)) {
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
        while (true) {
            Node<T> node = top.get();
            if (node == null) {
                return null;
            }
            if (node.pop(top)) {
                return node.value;
            }
        }
    }
}
