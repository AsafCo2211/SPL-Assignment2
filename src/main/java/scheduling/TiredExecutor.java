package scheduling;

import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;

public class TiredExecutor {

    private final TiredThread[] workers;
    private final PriorityBlockingQueue<TiredThread> idleMinHeap = new PriorityBlockingQueue<>();
    private final AtomicInteger inFlight = new AtomicInteger(0);

    public TiredExecutor(int numThreads) {
        // TODO
        if (numThreads <= 0) {
            throw new IllegalArgumentException("Number of threads must be positive.");
        }

        this.workers = new TiredThread[numThreads];
        for (int i = 0; i < numThreads; i++) {
            TiredThread worker = new TiredThread(i, 0.5 + Math.random());
            this.workers[i] = worker;
            this.idleMinHeap.add(worker);
            worker.start();
        }
    }

    public void submit(Runnable task) {
        // TODO
        if (task == null) {
            throw new IllegalArgumentException("Task cannot be null.");
        }
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

    public void submitAll(Iterable<Runnable> tasks) {
        if (tasks == null) {
            throw new IllegalArgumentException("Tasks cannot be null.");
        }
        // TODO: submit tasks one by one and wait until all finish
        AtomicInteger tasksCount = new AtomicInteger(0);
        Object lock = new Object();

        for (Runnable task : tasks) {
            tasksCount.incrementAndGet();

            Runnable wrappedTasks = () -> {
                try {
                    task.run();
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
    }

    public void shutdown() throws InterruptedException {
        // TODO
        for (TiredThread worker : workers) {
            worker.shutdown();
        }
        for (TiredThread worker : workers) {
            worker.join();
        }
    }

    public synchronized String getWorkerReport() {
        // TODO: return readable statistics for each worker
        
        StringBuilder report = new StringBuilder();
        report.append("------------------------------------------------------------\n");
        report.append("Worker Report (Fairness & Fatigue):\n");
        for (TiredThread worker : workers) {
            report.append(String.format("Worker #%d [%s]: \t Time Used: %d \t Time Idle: %d \t Total Fatigue: %.2f\n",
            worker.getWorkerId(),    
            worker.getName(),        
            worker.getTimeUsed(),    
            worker.getTimeIdle(),    
            worker.getFatigue()
            ));
        }
        report.append("------------------------------------------------------------\n");
        return report.toString();
    }   
}
