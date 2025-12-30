package scheduling;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.util.concurrent.atomic.AtomicBoolean;

class TiredThreadTest {

    @Test
    void testInitialization() {
        // Arrange: Create a worker but DO NOT start it
        int id = 10;
        double fatigueFactor = 1.5;

        // Act: Create the object (still not starting it)
        TiredThread worker = new TiredThread(id, fatigueFactor);

        // Assert: Verify initial state
        assertEquals(id, worker.getWorkerId(), "Worker ID should match constructor input");
        assertEquals(0, worker.getTimeUsed(), "New worker should have 0 time used");
        assertEquals(0, worker.getTimeIdle(), "New worker should have 0 idle time recorded initially");
        assertFalse(worker.isBusy(), "Worker should not be busy upon creation");

        // Verify name format (FF=%.2f)
        assertEquals("FF=1.50", worker.getName(), "Thread name should be formatted correctly");
    }

    @Test
    void testNewTaskSuccess() {
        // Arrange
        TiredThread worker = new TiredThread(1, 1.0);
        Runnable dummyTask = () -> {};

        // Act & Assert
        // The queue is empty (size 1), so this should succeed immediately
        assertDoesNotThrow(() -> worker.newTask(dummyTask), 
            "Adding a task to an empty queue should not throw an exception");
    }

    @Test
    void testNewTaskQueueFull() {
        // Arrange
        TiredThread worker = new TiredThread(2, 1.0);
        Runnable dummyTask = () -> {};

        // Fill the queue (capacity is 1)
        // We DO NOT start the thread, so the task remains in the queue
        worker.newTask(dummyTask);

        // Act & Assert
        // Trying to add a second task while the first one is still there should fail
        assertThrows(IllegalStateException.class, () -> worker.newTask(dummyTask),
            "Adding to a full queue should throw IllegalStateException");
    }

    @Test
    void testTaskExecution() throws InterruptedException {
        // Arrange
        TiredThread worker = new TiredThread(3, 1.0);
        AtomicBoolean wasRun = new AtomicBoolean(false);
        worker.start(); // Start the thread logic

        // Act
        worker.newTask(() -> wasRun.set(true));

        // Sleep briefly to let the worker thread pick up the task and run it
        Thread.sleep(100);

        // Assert
        assertTrue(wasRun.get(), "The task should have been executed and the flag set to true");
        
        // Cleanup
        worker.shutdown();
    }

    @Test
    void testTimeUsedCalculation() throws InterruptedException {
        // Arrange
        TiredThread worker = new TiredThread(4, 1.0);
        worker.start();

        // Act
        // Send a task that sleeps for 100ms
        worker.newTask(() -> {
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });

        // Test thread sleeps for 200ms to ensure worker finished the 100ms task
        Thread.sleep(200);

        // Assert
        long timeUsed = worker.getTimeUsed();
        // We expect at least 100ms (100,000,000 nanoseconds)
        assertTrue(timeUsed >= 100_000_000, 
            "Time used should be at least 100ms (converted to nanos). Actual: " + timeUsed);
        
        worker.shutdown();
    }

    @Test
    void testTimeIdleCalculation() throws InterruptedException {
        // Arrange
        TiredThread worker = new TiredThread(5, 1.0);
        worker.start();

        // Act
        // 1. Let the worker sit idle for 100ms
        Thread.sleep(100);

        // 2. Send a dummy task. Why? 
        // Because the worker only calculates/updates idle time AFTER it wakes up to take a task.
        worker.newTask(() -> {}); 

        // 3. Wait a bit for the update to happen
        Thread.sleep(50);

        // Assert
        long timeIdle = worker.getTimeIdle();
        assertTrue(timeIdle >= 100_000_000, 
            "Worker was idle for ~100ms, so idle time should reflect that. Actual: " + timeIdle);
            
        worker.shutdown();
    }

    @Test
    void testExceptionHandlingInTask() throws InterruptedException {
        // Arrange
        TiredThread worker = new TiredThread(6, 1.0);
        worker.start();
        AtomicBoolean secondTaskSuccess = new AtomicBoolean(false);

        // Act
        // 1. Send a task that crashes on purpose
        worker.newTask(() -> { throw new RuntimeException("Oops!"); });
        
        // Wait a bit to ensure it crashed and recovered
        Thread.sleep(50);

        // 2. Send a second task to verify the thread is still alive
        worker.newTask(() -> secondTaskSuccess.set(true));
        
        // Wait for the second task
        Thread.sleep(50);

        // Assert
        assertTrue(secondTaskSuccess.get(), 
            "Worker should survive an exception in a task and process the next one");
            
        worker.shutdown();
    }

    @Test
    void testIsBusyState() throws InterruptedException {
        // Arrange
        TiredThread worker = new TiredThread(7, 1.0);
        worker.start();

        // Act
        // Task sleeps for 200ms
        worker.newTask(() -> {
            try {
                Thread.sleep(200);
            } catch (InterruptedException e) { }
        });

        // Check 1: We check immediately (after 50ms), task should be running
        Thread.sleep(50); 
        assertTrue(worker.isBusy(), "Worker should be BUSY while task is running");

        // Check 2: We wait until task is definitely done (total > 200ms)
        Thread.sleep(250); 
        assertFalse(worker.isBusy(), "Worker should be IDLE after task finishes");
        
        worker.shutdown();
    }

    @Test
    void testShutdown() throws InterruptedException {
        // Arrange
        TiredThread worker = new TiredThread(8, 1.0);
        worker.start();

        // Act
        // Request the worker to stop
        worker.shutdown();

        // Wait for the thread to die (join waits for the thread to finish execution)
        // We give it a 1000ms timeout just in case it gets stuck, to prevent the test from hanging
        worker.join(1000);

        // Assert
        assertFalse(worker.isAlive(), "Worker thread should be dead after shutdown");
    }

    @Test
    void testShutdownWhileWorking() throws InterruptedException {
        // Arrange
        TiredThread worker = new TiredThread(9, 1.0);
        worker.start();
        AtomicBoolean taskCompleted = new AtomicBoolean(false);

        // Act
        // 1. Submit a task that takes some time (200ms)
        worker.newTask(() -> {
            try {
                Thread.sleep(200);
                taskCompleted.set(true);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });

        // 2. Immediately request shutdown. 
        // The "Poison Pill" should be queued AFTER the current task.
        worker.shutdown();

        // 3. Wait for the thread to finish everything and die
        worker.join(1000);

        // Assert
        assertTrue(taskCompleted.get(), 
            "Worker should finish the current task before shutting down (Graceful Shutdown)");
        assertFalse(worker.isAlive(), 
            "Worker thread should be dead after finishing the task");
    }

    @Test
    void testFatigueCalculation() throws InterruptedException {
        // Arrange
        double factor = 1.5;
        TiredThread worker = new TiredThread(10, factor);
        worker.start();

        // Act
        // Run a task for roughly 50ms
        worker.newTask(() -> {
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) { }
        });

        // Wait for task to finish
        Thread.sleep(100);

        // Assert
        long actualTimeUsed = worker.getTimeUsed();
        double expectedFatigue = actualTimeUsed * factor;
        double actualFatigue = worker.getFatigue();

        // Use a small delta for double comparison just in case of floating point minor errors
        assertEquals(expectedFatigue, actualFatigue, 0.0001, 
            "Fatigue should be exactly timeUsed * fatigueFactor");
        
        worker.shutdown();
    }

    @Test
    void testCompareTo() throws InterruptedException {
        // Arrange
        // t1 will have low fatigue (factor 1)
        TiredThread t1 = new TiredThread(11, 1.0);
        // t2 will have high fatigue (factor 100)
        TiredThread t2 = new TiredThread(12, 100.0); 

        t1.start();
        t2.start();

        // Act
        // Give both the exact same task (sleep 20ms)
        Runnable task = () -> {
            try {
                Thread.sleep(20);
            } catch (InterruptedException e) { }
        };

        t1.newTask(task);
        t2.newTask(task);

        // Wait for both to finish
        Thread.sleep(100);

        // Assert
        // t1 (factor 1) should be "less" than t2 (factor 100)
        assertTrue(t1.compareTo(t2) < 0, "t1 should be less tired (smaller value) than t2");
        
        // t2 should be "greater" than t1
        assertTrue(t2.compareTo(t1) > 0, "t2 should be more tired (larger value) than t1");
        
        // Comparison to self should be 0
        assertEquals(0, t1.compareTo(t1), "Comparison to self should be 0");

        t1.shutdown();
        t2.shutdown();
    }
}