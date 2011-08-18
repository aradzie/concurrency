package stack;

public class FlatCombiningStackTest extends ConcurrentStackTestCase {
    @Override
    protected <T> ConcurrentStack<T> create() {
        return new FlatCombiningStack<T>();
    }
}
