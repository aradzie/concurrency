package stack;

public class EliminationBackoffStackTest extends ConcurrentStackTestCase {
    @Override
    protected <T> ConcurrentStack<T> create() {
        return new EliminationBackoffStack<T>();
    }
}
