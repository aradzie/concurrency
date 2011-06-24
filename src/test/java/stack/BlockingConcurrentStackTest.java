package stack;

public class BlockingConcurrentStackTest extends ConcurrentStackTestCase {
    @Override
    protected <T> ConcurrentStack<T> create() {
        return new ConcurrentStack.Blocking<T>();
    }
}
