package stack;

public class BackoffLockFreeStackTest extends ConcurrentStackTestCase {
    @Override
    protected <T> ConcurrentStack<T> create() {
        return new BackoffLockFreeStack<T>();
    }
}
