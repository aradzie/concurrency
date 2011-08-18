package stack;

import javax.annotation.Nonnull;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public final class FlatCombiningStack<T> implements ConcurrentStack<T> {
    private static final class Node<T> {
        final T value;
        final Node<T> next;

        Node(T value, Node<T> next) {
            this.value = value;
            this.next = next;
        }
    }

    private enum OpCode {
        PUSH,
        PEEK,
        POP
    }

    private static final class Op<T> {
        volatile Op<T> next;
        final OpCode opCode;
        volatile T value;
        volatile boolean ready;

        Op(OpCode opCode, T value) {
            this.opCode = opCode;
            this.value = value;
        }

        void enqueue(FlatCombiningStack<T> stack) {
            while (true) {
                next = stack.queue.get();
                if (stack.queue.compareAndSet(next, this)) {
                    break;
                }
            }
        }

        void invoke(FlatCombiningStack<T> stack) {
            if (ready) {
                return;
            }
            switch (opCode) {
                case PUSH:
                    stack.head = new Node<T>(value, stack.head);
                    break;
                case PEEK:
                    if (stack.head != null) {
                        value = stack.head.value;
                    }
                    else {
                        value = null;
                    }
                    break;
                case POP:
                    if (stack.head != null) {
                        value = stack.head.value;
                        stack.head = stack.head.next;
                    }
                    else {
                        value = null;
                    }
                    break;
            }
            ready = true;
        }
    }

    private final Lock lock;
    private final AtomicReference<Op<T>> queue;
    private Node<T> head;

    public FlatCombiningStack() {
        this(new ReentrantLock());
    }

    public FlatCombiningStack(Lock lock) {
        this.lock = lock;
        queue = new AtomicReference<Op<T>>();
    }

    @Override
    public void push(@Nonnull T e) {
        if (e == null) {
            throw new NullPointerException();
        }
        Op<T> op = new Op<T>(OpCode.PUSH, e);
        process(op);
    }

    @Override
    public T peek() {
        Op<T> op = new Op<T>(OpCode.PEEK, null);
        process(op);
        return op.value;
    }

    @Override
    public T pop() {
        Op<T> op = new Op<T>(OpCode.POP, null);
        process(op);
        return op.value;
    }

    private void process(Op<T> op) {
        op.enqueue(this);

        while (!op.ready) {
            if (lock.tryLock()) {
                try {
                    scanCombine();
                }
                finally {
                    lock.unlock();
                }
                break;
            }

            Thread.yield();
        }
    }

    private void scanCombine() {
        Op<T> op = queue.get();
        while (op != null) {
            op.invoke(this);
            Op<T> next = op.next;
            op.next = null;
            op = next;
        }
    }
}
