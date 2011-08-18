package stack;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public final class FlatCombiningStack<T> implements ConcurrentStack<T> {
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
            do {
                next = stack.queue.get();
            } while (!stack.queue.compareAndSet(next, this));
        }

        void invoke(FlatCombiningStack<T> stack) {
            switch (opCode) {
                case PUSH:
                    stack.list.add(value);
                    break;
                case PEEK:
                    if (stack.list.isEmpty()) {
                        value = null;
                    }
                    else {
                        value = stack.list.get(stack.list.size() - 1);
                    }
                    break;
                case POP:
                    if (stack.list.isEmpty()) {
                        value = null;
                    }
                    else {
                        value = stack.list.remove(stack.list.size() - 1);
                    }
                    break;
            }
        }
    }

    private final Lock lock;
    private final AtomicReference<Op<T>> queue;
    private final ArrayList<T> list;

    public FlatCombiningStack() {
        lock = new ReentrantLock();
        queue = new AtomicReference<Op<T>>();
        list = new ArrayList<T>();
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
        Op<T> lastHead = null;
        for (int round = 0; round < 20; round++) {
            Op<T> head = queue.get();
            if (head == lastHead) {
                break;
            }
            Op<T> op = head;
            while (op != lastHead) {
                if (!op.ready) {
                    op.invoke(this);
                    op.ready = true;
                }
                op = op.next;
            }
            head.next = null;
            lastHead = head;
        }
    }
}
