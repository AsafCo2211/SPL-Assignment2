package scheduling;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

public class TiredThread extends Thread implements Comparable<TiredThread> {

    private static final Runnable POISON_PILL = () -> {}; // Special task to signal shutdown

    private final int id; // Worker index assigned by the executor
    private final double fatigueFactor; // Multiplier for fatigue calculation

    private final AtomicBoolean alive = new AtomicBoolean(true); // Indicates if the worker should keep running

    // Single-slot handoff queue; executor will put tasks here
    private final BlockingQueue<Runnable> handoff = new ArrayBlockingQueue<>(1);

    private final AtomicBoolean busy = new AtomicBoolean(false); // Indicates if the worker is currently executing a task

    private final AtomicLong timeUsed = new AtomicLong(0); // Total time spent executing tasks
    private final AtomicLong timeIdle = new AtomicLong(0); // Total time spent idle
    private final AtomicLong idleStartTime = new AtomicLong(0); // Timestamp when the worker became idle

    public TiredThread(int id, double fatigueFactor) {
        this.id = id;
        this.fatigueFactor = fatigueFactor;
        this.idleStartTime.set(System.nanoTime());
        setName(String.format("FF=%.2f", fatigueFactor));
    }

    public int getWorkerId() {
        return id;
    }

    public double getFatigue() {
        return fatigueFactor * timeUsed.get();
    }

    public boolean isBusy() {
        return busy.get();
    }

    public long getTimeUsed() {
        return timeUsed.get();
    }

    public long getTimeIdle() {
        return timeIdle.get();
    }

    /**
     * Assign a task to this worker.
     * This method is non-blocking: if the worker is not ready to accept a task,
     * it throws IllegalStateException.
     */
    public void newTask(Runnable task) {
       // TODO
        this.handoff.add(task);
    }

    /**
     * Request this worker to stop after finishing current task.
     * Inserts a poison pill so the worker wakes up and exits.
     */
    public void shutdown() {
       // TODO
       try{
               this.handoff.put(POISON_PILL);
       } catch (InterruptedException e) {
               Thread.currentThread().interrupt();
       }
    }

    @Override
    public void run() {
       // TODO
       try {
            while (alive.get()) {
                // 1. Waiting for a task (the thread blocks here)
                Runnable task = handoff.take();

                // 2. Woken up! Measuring idle time
                long now = System.nanoTime();
                long idleDuration = now - idleStartTime.get();
                timeIdle.addAndGet(idleDuration);

                // 3. Checking for poison pill
                if (task == POISON_PILL) {
                    alive.set(false);
                    break;
                } else {
                    // 4. Executing the task
                    busy.set(true);
                    long workStart = System.nanoTime();
                    try {
                        task.run();
                    } catch (Exception e) {
                        System.err.println("Worker " + id + " failed task: " + e.getMessage());
                    } finally {
                        // 5. Measuring work time and resetting the idle timer
                        long workEnd = System.nanoTime();
                        long workDuration = workEnd - workStart;
                        timeUsed.addAndGet(workDuration);

                        // Resetting state in preparation for the next idle period
                        busy.set(false);
                        idleStartTime.set(System.nanoTime());
                    }
                }
            }
       } catch (InterruptedException e) {
            alive.set(false);
       }
    }

    @Override
    public int compareTo(TiredThread o) {
        // TODO

        return Double.compare(this.getFatigue(), o.getFatigue());
    }
}