package casn;

import casn.CASNRef.Cell;
import util.Backoff;

import java.util.AbstractList;
import java.util.Iterator;
import java.util.ListIterator;
import java.util.NoSuchElementException;

import static casn.CASNRef.Cell.cell;

/**
 * A list implementation that does not use locks but employs
 * {@link CASNRef n-location compare-and-set} operations to
 * maintain internal consistency of nodes.
 * <p/>
 * It is unknown if this class is useful in practice, its performance
 * has not been measured, the design may be suboptimal and it may be
 * full of bugs.
 *
 * @param <E> List element type.
 */
public class LockFreeLinkedList<E> extends AbstractList<E> {
    /**
     * Single list node that links
     * to the previous and next one.
     */
    private static class Node<E> {
        final CASNRef<Node<E>> prev, next;
        volatile E value;

        Node() {
            prev = new CASNRef<Node<E>>(this);
            next = new CASNRef<Node<E>>(this);
        }

        Node(Node<E> prev, Node<E> next) {
            this.prev = new CASNRef<Node<E>>(prev);
            this.next = new CASNRef<Node<E>>(next);
        }

        Node(Node<E> prev, Node<E> next, E value) {
            this(prev, next);
            this.value = value;
        }

        Node<E> nth(int index) {
            Node<E> node = this;
            for (int i = 0; i <= index; i++) {
                node = node.next.get();
                if (node == this) {
                    break;
                }
            }
            return node;
        }

        /**
         * Insert new node with the specified element before this node.
         *
         * @param size The ref holding list size to update.
         * @param e    The element to insert before this node.
         * @return Success flag.
         */
        boolean prepend(CASNRef<Integer> size, E e) {
            Node<E> prev = this.prev.get();
            Node<E> node = new Node<E>(prev, this, e);
            Integer s = size.get();
            Cell l =
                    cell(size, s, s + 1,
                            cell(prev.next, this, node,
                                    cell(this.prev, prev, node)));
            return CASNRef.casn(l);
        }

        /**
         * Remove this node from list.
         *
         * @param size The ref holding list size to update.
         * @return Success flag.
         */
        boolean remove(CASNRef<Integer> size) {
            Node<E> prev = this.prev.get();
            Node<E> next = this.next.get();
            Integer s = size.get();
            Cell l =
                    cell(size, s, s - 1,
                            cell(prev.next, this, next,
                                    cell(next.prev, this, prev)));
            return CASNRef.casn(l);
        }

        E swap(E v) {
            E r = value;
            value = v;
            return r;
        }
    }

    private final Node<E> head;
    private final CASNRef<Integer> size;
    private volatile Backoff backoff;

    public LockFreeLinkedList() {
        head = new Node<E>();
        size = new CASNRef<Integer>(0);
    }

    @Override
    public E get(int index) {
        Node<E> node = head.nth(index);
        if (node == head) {
            throw new IndexOutOfBoundsException();
        }
        return node.value;
    }

    @Override
    public E set(int index, E e) {
        Node<E> node = head.nth(index);
        if (node == head) {
            throw new IndexOutOfBoundsException();
        }
        return node.swap(e);
    }

    @Override
    public boolean add(E e) {
        while (true) {
            if (head.prepend(size, e)) {
                break;
            }
            backoff = Backoff.backoff(backoff);
        }
        return true;
    }

    @Override
    public void add(int index, E e) {
        while (true) {
            if (head.nth(index).prepend(size, e)) {
                return;
            }
            backoff = Backoff.backoff(backoff);
        }
    }

    @Override
    public boolean contains(Object o) {
        return indexOf(o) != -1;
    }

    @Override
    public int indexOf(Object o) {
        int index = 0;
        Node<E> node = head.next.get();
        while (node != head) {
            if (equals(node.value, o)) {
                return index;
            }
            index++;
            node = node.next.get();
        }
        return -1;
    }

    @Override
    public int lastIndexOf(Object o) {
        int index = size.get() - 1;
        Node<E> node = head.prev.get();
        while (node != head) {
            if (equals(node.value, o)) {
                return index;
            }
            index--;
            node = node.prev.get();
        }
        return -1;
    }

    @Override
    public E remove(int index) {
        while (true) {
            int n = 0;
            Node<E> node = head.next.get();
            while (true) {
                if (node == head) {
                    throw new IndexOutOfBoundsException();
                }
                if (n == index) {
                    if (node.remove(size)) {
                        return node.value;
                    }
                    else {
                        break;
                    }
                }
                n++;
                node = node.next.get();
            }
        }
    }

    @Override
    public boolean remove(Object o) {
        while (true) {
            Node<E> node = head.next.get();
            while (true) {
                if (node == head) {
                    return false;
                }
                if (equals(node.value, o)) {
                    if (node.remove(size)) {
                        return true;
                    }
                    else {
                        break;
                    }
                }
                node = node.next.get();
            }
        }
    }

    @Override
    public int size() {
        return size.get();
    }

    @Override
    public void clear() {
        while (true) {
            Node<E> prev = head.prev.get();
            Node<E> next = head.next.get();
            Cell l =
                    cell(size, size.get(), 0,
                            cell(head.prev, prev, head,
                                    cell(head.next, next, head)));
            if (CASNRef.casn(l)) {
                break;
            }
        }
    }

    @Override
    public Iterator<E> iterator() {
        return new IteratorImpl(0);
    }

    @Override
    public ListIterator<E> listIterator() {
        return new IteratorImpl(0);
    }

    @Override
    public ListIterator<E> listIterator(int index) {
        return new IteratorImpl(index);
    }

    private static boolean equals(Object a, Object b) {
        return a == null && b == null || a != null && b != null && a.equals(b);
    }

    /**
     * The iterator is single-threaded, it cannot be safely shared
     * between threads.
     */
    private class IteratorImpl implements ListIterator<E> {
        private Node<E> last, next;

        IteratorImpl(int i) {
            last = head;
            next = last.nth(i);
        }

        @Override
        public boolean hasNext() {
            return next != head;
        }

        @Override
        public E next() {
            if (next == head) {
                throw new NoSuchElementException();
            }
            last = next;
            next = next.next.get();
            return last.value;
        }

        @Override
        public boolean hasPrevious() {
            return next.prev.get() != head;
        }

        @Override
        public E previous() {
            Node<E> prev = last.prev.get();
            if (prev == head) {
                throw new NoSuchElementException();
            }
            last = next = prev;
            return last.value;
        }

        @Override
        public int nextIndex() {
            throw new UnsupportedOperationException();
        }

        @Override
        public int previousIndex() {
            throw new UnsupportedOperationException();
        }

        @Override
        public void remove() {
            if (last == head) {
                throw new IllegalStateException();
            }
            while (true) {
                if (last.remove(size)) {
                    if (next == last) {
                        next = last.next.get();
                    }
                    else {
                        last = head;
                    }
                    break;
                }
            }
        }

        @Override
        public void set(E e) {
            if (last == head) {
                throw new IllegalStateException();
            }
            next.swap(e);
        }

        @Override
        public void add(E e) {
            last = head;
            while (true) {
                if (next.prepend(size, e)) {
                    break;
                }
            }
        }
    }
}
