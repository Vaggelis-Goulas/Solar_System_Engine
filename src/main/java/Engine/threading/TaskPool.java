package Engine.threading;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * A lightweight fixed-size worker pool sized to the available CPU cores.
 *
 * <p>All rendering work (GL calls) must stay on the main thread; this pool is strictly for
 * CPU-bound background work such as procedural generation. Threads are daemon threads so a
 * forgotten {@link #shutdown()} never blocks JVM exit.</p>
 */
public final class TaskPool {

    /** Unchecked wrapper for failures inside pooled tasks. */
    public static class TaskException extends RuntimeException {
        public TaskException(Throwable cause) {
            super(cause);
        }
    }

    private static final int CORES = Math.max(1, Runtime.getRuntime().availableProcessors());

    private static final String THREAD_NAME = "solar-worker";

    private static volatile TaskPool instance;

    private final ExecutorService executor;
    private final int size;
    private final java.util.concurrent.atomic.AtomicInteger counter = new java.util.concurrent.atomic.AtomicInteger();

    private TaskPool(int size) {
        this.size = size;
        this.executor = Executors.newFixedThreadPool(size, r -> {
            Thread t = new Thread(r, THREAD_NAME + "-" + counter.incrementAndGet());
            t.setDaemon(true);
            return t;
        });
    }

    /**
     * Return the shared pool. Created lazily and sized to the number of logical cores.
     */
    public static TaskPool getInstance() {
        TaskPool pool = instance;
        if (pool == null) {
            synchronized (TaskPool.class) {
                pool = instance;
                if (pool == null) {
                    pool = new TaskPool(CORES);
                    instance = pool;
                    System.out.println("[TaskPool] Started " + pool.size
                            + " worker threads (" + CORES + " logical cores)");
                }
            }
        }
        return pool;
    }

    /** Number of worker threads. */
    public int size() {
        return size;
    }

    /** Submit a result-producing task. */
    public <T> Task<T> submit(java.util.concurrent.Callable<T> callable) {
        return new Task<>(executor.submit(callable));
    }

    /** Submit a fire-and-forget task. */
    public Task<Void> submit(Runnable runnable) {
        return new Task<>(executor.submit(runnable, null));
    }

    /** Submit a block of work that takes an iteration index (used by {@link Parallel}). */
    public <T> Task<T> submitIndexed(int index, java.util.function.IntFunction<T> work) {
        return new Task<>(executor.submit(() -> work.apply(index)));
    }

    /**
     * Gracefully stop accepting new tasks and wait for outstanding work to finish.
     * Safe to call multiple times.
     */
    public void shutdown() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            executor.shutdownNow();
        }
    }

    /** Whether the pool has been shut down. */
    public boolean isShutdown() {
        return executor.isShutdown();
    }
}