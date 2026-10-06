package Engine.threading;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Handle to a task running on a {@link TaskPool}. Thin wrapper over {@link Future} that
 * converts checked exceptions into an unchecked form so call sites stay clean.
 *
 * @param <T> result type (use {@link Void} for fire-and-forget work)
 */
public final class Task<T> {

    private final Future<T> future;

    Task(Future<T> future) {
        this.future = future;
    }

    /** Whether the task has completed (successfully, exceptionally, or by cancellation). */
    public boolean isDone() {
        return future.isDone();
    }

    public boolean isCancelled() {
        return future.isCancelled();
    }

    public void cancel(boolean mayInterruptIfRunning) {
        future.cancel(mayInterruptIfRunning);
    }

    /** Block until the task finishes and return its result (unchecked exceptions). */
    public T get() {
        try {
            return future.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new TaskPool.TaskException(e);
        } catch (ExecutionException e) {
            throw new TaskPool.TaskException(e.getCause());
        }
    }

    /** Block until the task finishes, bounded by the given timeout. */
    public T get(long timeout, TimeUnit unit) {
        try {
            return future.get(timeout, unit);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new TaskPool.TaskException(e);
        } catch (ExecutionException e) {
            throw new TaskPool.TaskException(e.getCause());
        } catch (TimeoutException e) {
            throw new TaskPool.TaskException(e);
        }
    }

    /** Block until completion, discarding the result (fire-and-forget). */
    public void await() {
        get();
    }
}