package scheduling;

import java.util.concurrent.PriorityBlockingQueue;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public class TiredExecutor {

    private final TiredThread[] workers;
    private final PriorityBlockingQueue<TiredThread> idleMinHeap = new PriorityBlockingQueue<>();
    private final AtomicInteger inFlight = new AtomicInteger(0);

    public TiredExecutor(int numThreads) {
        this.workers = new TiredThread[numThreads];
        for (int i = 0; i < numThreads; i++) {
            TiredThread worker = new TiredThread(i, 0.5 + Math.random());
            this.workers[i] = worker;
            this.idleMinHeap.add(worker);
            worker.start();
        }
    }

    public void submit(Runnable task) {
        try {
            // Find an available worker.
            TiredThread worker = idleMinHeap.take();
            // Track ongoing tasks.
            this.inFlight.incrementAndGet();

            // We wrap the original task to inject cleanup logic that the worker 
            // must execute after finishing the real work.
            Runnable wrappedTask = () -> {
                try {
                    task.run();
                } finally {
                    this.inFlight.decrementAndGet();
                    idleMinHeap.add(worker);
                }
            };

            // Dispatch the wrapped task to the worker.
            try {
                worker.newTask(wrappedTask);
            } catch (RuntimeException e) {
                this.inFlight.decrementAndGet();
                idleMinHeap.add(worker);
                throw e; 
            }

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Submits every task, waits until all of them have finished, and rethrows
     * the first failure, if any. Workers survive a failing task, so without
     * this a failed row operation (e.g. a dimension mismatch detected inside
     * the task) would be silently dropped and the caller would carry on with
     * a partially computed matrix.
     */
    public void submitAll(Iterable<Runnable> tasks) {
        AtomicInteger tasksCount = new AtomicInteger(0);
        AtomicReference<RuntimeException> firstFailure = new AtomicReference<>();
        Object lock = new Object();

        for (Runnable task : tasks) {
            tasksCount.incrementAndGet();

            Runnable wrappedTasks = () -> {
                try {
                    task.run();
                } catch (RuntimeException e) {
                    firstFailure.compareAndSet(null, e);
                } finally {
                    if (tasksCount.decrementAndGet() == 0) {
                        synchronized (lock) {
                            lock.notifyAll();
                        }
                    }
                }
            };

            this.submit(wrappedTasks);
        }
        synchronized (lock) {
            while (tasksCount.get() > 0) {
                try {
                    lock.wait();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }

        RuntimeException failure = firstFailure.get();
        if (failure != null) {
            throw failure;
        }
    }

    public void shutdown() throws InterruptedException {
        for (TiredThread worker : workers) {
            worker.shutdown();
        }
        for (TiredThread worker : workers) {
            worker.join();
        }
    }

    public synchronized String getWorkerReport() {
        
        StringBuilder report = new StringBuilder();
        report.append("------------------------------------------------------------\n");
        report.append("Worker Report (Fairness & Fatigue):\n");
        for (TiredThread worker : workers) {
            // Times are measured in nanoseconds; shown in milliseconds.
            report.append(String.format("Worker #%d [%s]:  busy %8.2f ms   idle %8.2f ms   fatigue %8.2f%n",
                    worker.getWorkerId(),
                    worker.getName(),
                    worker.getTimeUsed() / 1e6,
                    worker.getTimeIdle() / 1e6,
                    worker.getFatigue() / 1e6));
        }
        report.append("------------------------------------------------------------\n");
        return report.toString();
    }   
}
