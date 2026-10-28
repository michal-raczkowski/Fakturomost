package dev.michalraczkowski.fakturomost.desktop.ui;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import javafx.concurrent.Task;

/**
 * Runs everything that touches the store or the disk off the JavaFX Application Thread (master
 * prompt §6). One worker thread: imports, detail loads and exports run one after another, so they
 * never see each other's half-finished work — and the SQLite store of M5 wants one writer anyway.
 *
 * <p>Callbacks run on the JavaFX thread ({@link Task} guarantees that for its event handlers).
 */
public final class Background implements AutoCloseable {

    private final ExecutorService worker;

    public Background() {
        AtomicInteger count = new AtomicInteger();
        ThreadFactory daemon = runnable -> {
            Thread thread = new Thread(runnable, "fakturomost-worker-" + count.incrementAndGet());
            thread.setDaemon(true); // closing the window must not wait for a long import
            return thread;
        };
        this.worker = Executors.newSingleThreadExecutor(daemon);
    }

    /**
     * @param onSuccess called with the task's value, on the JavaFX thread
     * @param onFailure called with the exception, on the JavaFX thread
     */
    public <T> Task<T> run(Task<T> task, Consumer<T> onSuccess, Consumer<Throwable> onFailure) {
        task.setOnSucceeded(event -> onSuccess.accept(task.getValue()));
        task.setOnFailed(event -> onFailure.accept(task.getException()));
        worker.execute(task);
        return task;
    }

    /**
     * Stops accepting work. A running import does not react to interrupts; it is a daemon thread,
     * so it ends with the JVM. Nothing is lost by that in M4: the in-memory store dies too.
     */
    @Override
    public void close() {
        worker.shutdownNow();
    }
}
