package stack;

public class LockFreeStackTest extends ConcurrentStackTestCase {
    @Override
    protected <T> ConcurrentStack<T> create() {
        return new LockFreeStack<T>();
    }
}
