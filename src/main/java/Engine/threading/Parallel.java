package Engine.threading;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.IntConsumer;

/**
 * Parallel execution helpers for safe, multicore data generation.
 *
 * <p>These helpers assume each element can be computed <em>independently</em> from an index
 * (embarrassingly parallel), which is exactly the shape of procedural generation. Work is
 * distributed with block-stealing: threads claim successive blocks of indices from a shared
 * cursor, so cores stay busy even when blocks have unequal cost. Results must be written to
 * disjoint slots (e.g. {@code array[i] = ...}) — never shared collections, which are not
 * thread-safe.</p>
 */
public final class Parallel {

    private Parallel() {}

    /** Smallest range length before we consider going parallel. */
    private static final int MIN_PARALLEL = 256;

    /** Steals per worker; larger values = better load balance, more atomic contention. */
    private static final int STEALS_PER_WORKER = 8;

    /**
     * Run {@code body} over every index in [{@code from}, {@code to}), splitting the work
     * across all worker threads. Runs inline (single-threaded) for tiny or single-core pools.
     */
    public static void forEach(int from, int to, IntConsumer body) {
        TaskPool pool = TaskPool.getInstance();
        int workers = pool.size();
        int count = to - from;
        if (count <= 0) return;
        // Adaptive block: aim for a small number of steals per worker so the shared cursor
        // stays nearly contention-free while still balancing unequal per-item costs.
        int block = Math.max(1, Math.min(1 << 16, count / (workers * STEALS_PER_WORKER)));
        forEach(from, to, block, workers, body);
    }

    /**
     * Run {@code body} over every index in [{@code from}, {@code to}) with a given block size.
     */
    public static void forEach(int from, int to, int blockSize, IntConsumer body) {
        TaskPool pool = TaskPool.getInstance();
        forEach(from, to, blockSize, pool.size(), body);
    }

    private static void forEach(int from, int to, int blockSize, int workers, IntConsumer body) {
        int count = to - from;
        if (count <= 0) return;

        if (count < MIN_PARALLEL || workers <= 1) {
            for (int i = from; i < to; i++) {
                body.accept(i);
            }
            return;
        }

        int block = Math.max(1, blockSize);
        AtomicInteger cursor = new AtomicInteger(from);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        // One submitted work item per worker; each steals blocks until the range is exhausted.
        java.util.concurrent.CountDownLatch done = new java.util.concurrent.CountDownLatch(workers);

        TaskPool pool = TaskPool.getInstance();
        for (int w = 0; w < workers; w++) {
            pool.submit(() -> {
                try {
                    while (failure.get() == null) {
                        int start = cursor.getAndAdd(block);
                        if (start >= to) break;
                        int end = Math.min(to, start + block);
                        for (int i = start; i < end; i++) {
                            body.accept(i);
                        }
                    }
                } catch (Throwable t) {
                    failure.compareAndSet(null, t);
                } finally {
                    done.countDown();
                }
            });
        }

        try {
            done.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new TaskPool.TaskException(e);
        }

        Throwable t = failure.get();
        if (t != null) {
            throw new TaskPool.TaskException(t);
        }
    }
}