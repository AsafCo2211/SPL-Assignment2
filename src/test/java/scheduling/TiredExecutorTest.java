package scheduling;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
class TiredExecutorTest {

    @Test
    void testConstructorInvalidThreadCount() {
        assertThrows(IllegalArgumentException.class, () -> new TiredExecutor(0), 
            "Creating TiredExecutor with 0 threads should throw IllegalArgumentException");

        assertThrows(IllegalArgumentException.class, () -> new TiredExecutor(-5), 
            "Creating TiredExecutor with negative threads should throw IllegalArgumentException");
    }

    @Test
    void testInitializationAndReport() {
        // Arrange: Create executor with exactly 3 threads
        int numThreads = 3;

        TiredExecutor executor = new TiredExecutor(numThreads);

        // Act: Get the internal status report
        String report = executor.getWorkerReport();

        // Assert: Verify that the report contains entries for all expected workers
        assertNotNull(report, "Report should not be null");
        assertTrue(report.contains("Worker #0"), "Report should contain Worker #0");
        assertTrue(report.contains("Worker #1"), "Report should contain Worker #1");
        assertTrue(report.contains("Worker #2"), "Report should contain Worker #2");
        
        // Assert: Verify it does not contain extra workers
        assertFalse(report.contains("Worker #3"), "Report should NOT contain Worker #3");
    }

    @Test
    void testSubmitNullTask() {
        // Arrange
        TiredExecutor executor = new TiredExecutor(2);

        // Act & Assert
        assertThrows(IllegalArgumentException.class, () -> executor.submit(null), 
            "Submitting a null task should throw IllegalArgumentException");
    }

    @Test
    void testSubmitSingleTask() throws InterruptedException {
        // Arrange: Create executor with a single thread
        TiredExecutor executor = new TiredExecutor(1);
        AtomicBoolean taskExecuted = new AtomicBoolean(false);

        // Act: Submit a simple task that flips the boolean flag
        executor.submit(() -> taskExecuted.set(true));

        // Allow some time for the asynchronous task to complete
        Thread.sleep(100);

        // Assert: The flag should be true
        assertTrue(taskExecuted.get(), "Task should be executed and set flag to true");
        
        // Cleanup
        executor.shutdown();
    }

    // --- Part 2: Advanced Execution & Pooling Logic ---

    @Test
    void testThreadPoolRecycling() throws InterruptedException {
        // Arrange: Pool with only 2 threads
        int numThreads = 2;
        int totalTasks = 10;
        TiredExecutor executor = new TiredExecutor(numThreads);
        AtomicInteger completedTasks = new AtomicInteger(0);

        // Act: Submit 10 tasks. 
        // Logic check: If workers are not returned to the pool, tasks 3-10 will never run.
        for (int i = 0; i < totalTasks; i++) {
            executor.submit(() -> {
                try {
                    Thread.sleep(10); // Simulate work
                } catch (InterruptedException e) {}
                completedTasks.incrementAndGet();
            });
        }

        // Wait enough time for all tasks to complete
        Thread.sleep(1000);

        // Assert: All tasks must be finished
        assertEquals(totalTasks, completedTasks.get(), 
            "All tasks should finish. If failed, workers are likely not returning to the pool.");
        
        executor.shutdown();
    }

    @Test
    void testHighLoadExecution() throws InterruptedException {
        // Arrange: Standard pool of 4 threads
        TiredExecutor executor = new TiredExecutor(4);
        int totalTasks = 100; // Many tasks
        AtomicInteger completedTasks = new AtomicInteger(0);

        // Act: Rapidly submit 100 tasks
        for (int i = 0; i < totalTasks; i++) {
            executor.submit(() -> completedTasks.incrementAndGet());
        }

        // Wait for completion (give it ample time)
        Thread.sleep(2000);

        // Assert
        assertEquals(totalTasks, completedTasks.get(), 
            "Executor should handle high load without losing tasks.");
        
        executor.shutdown();
    }

    // --- Part 3: Group Execution (submitAll) ---
    @Test
    void testSubmitAllWithNullIterable() {
        // Arrange
        TiredExecutor executor = new TiredExecutor(2);

        // Act & Assert
        assertThrows(IllegalArgumentException.class, () -> executor.submitAll(null), 
            "Submitting a null iterable to submitAll should throw IllegalArgumentException");
    }

    @Test
    void testSubmitAllExecution() throws InterruptedException {
        // Arrange: Executor with 3 threads
        TiredExecutor executor = new TiredExecutor(3);
        int numberOfTasks = 5;
        AtomicInteger counter = new AtomicInteger(0);
        List<Runnable> tasks = new ArrayList<>();

        // Create 5 tasks with different sleep times to simulate real work
        for (int i = 0; i < numberOfTasks; i++) {
            tasks.add(() -> {
                try {
                    // Random sleep between 20ms and 50ms
                    Thread.sleep(20 + (long)(Math.random() * 30)); 
                    counter.incrementAndGet();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
        }

        // Act: This method MUST BLOCK until all 5 tasks are done.
        executor.submitAll(tasks);

        // Assert:
        // If we reached this line, submitAll has returned.
        // We verify that ALL tasks are indeed finished.
        assertEquals(numberOfTasks, counter.get(), 
            "submitAll should not return until ALL tasks are completed.");
        
        executor.shutdown();
    }

    @Test
    void testSubmitAllWithEmptyList() throws InterruptedException {
        // Arrange
        TiredExecutor executor = new TiredExecutor(2);
        List<Runnable> emptyList = new ArrayList<>();

        // Act & Assert
        // This should return immediately and not hang/deadlock
        assertDoesNotThrow(() -> executor.submitAll(emptyList), 
            "submitAll with an empty list should return immediately without errors");

        executor.shutdown();
    }

    // --- Part 4: Error Handling & Resilience ---

    @Test
    void testTaskExceptionDoesNotKillWorker() throws InterruptedException {
        // Arrange: Pool with 1 thread
        TiredExecutor executor = new TiredExecutor(1);
        AtomicBoolean secondTaskRan = new AtomicBoolean(false);

        // Act 1: Submit a task that crashes nicely
        // We expect the worker to catch this, clean up, and return to the pool.
        executor.submit(() -> {
            throw new RuntimeException("Intentional Crash!");
        });

        // Sleep briefly to let the crash happen and cleanup logic to run
        Thread.sleep(100);

        // Act 2: Submit a normal task. 
        // If the worker died from the previous exception, this task will never start.
        executor.submit(() -> secondTaskRan.set(true));

        // Wait for second task
        Thread.sleep(100);

        // Assert
        assertTrue(secondTaskRan.get(), 
            "The worker should survive a task exception and be available for the next task.");

        executor.shutdown();
    }

    @Test
    void testSubmitAllWithMixedFailures() {
        // Arrange: Executor with 3 threads
        TiredExecutor executor = new TiredExecutor(3);
        AtomicInteger successCounter = new AtomicInteger(0);
        List<Runnable> tasks = new ArrayList<>();

        // Create 5 tasks: Indices 0, 2, 4 succeed. Indices 1, 3 fail.
        for (int i = 0; i < 5; i++) {
            final int index = i;
            tasks.add(() -> {
                if (index % 2 != 0) {
                    throw new RuntimeException("Task " + index + " failed!");
                }
                successCounter.incrementAndGet();
            });
        }

        // Act & Assert
        // This is the critical check: submitAll must NOT hang forever even if tasks fail.
        // It relies on the 'finally' block in submitAll to decrement the counter regardless of success/failure.
        assertDoesNotThrow(() -> executor.submitAll(tasks), 
            "submitAll should return normally even if some tasks throw exceptions");

        // Verify that the 3 successful tasks actually finished
        assertEquals(3, successCounter.get(), "The 3 valid tasks should have completed successfully");
        
        try {
            executor.shutdown();
        } catch (InterruptedException e) {}
    }

    @Test
    void testSequentialFailures() throws InterruptedException {
        // Arrange: Pool with 1 thread
        TiredExecutor executor = new TiredExecutor(1);
        int failureCount = 5;
        AtomicBoolean finalSuccess = new AtomicBoolean(false);

        // Act: Bombard the worker with 5 failing tasks in a row
        for (int i = 0; i < failureCount; i++) {
            executor.submit(() -> {
                throw new RuntimeException("Boom!");
            });
        }

        // Give it a moment to process failures
        Thread.sleep(100);

        // Submit one final good task
        executor.submit(() -> finalSuccess.set(true));
        
        // Wait for the good task
        Thread.sleep(100);

        // Assert: The worker should remain resilient even after multiple failures
        assertTrue(finalSuccess.get(), 
            "Worker should survive multiple sequential crashes and process the final task");

        executor.shutdown();
    }

    // --- Part 5: Priority & Fairness Analysis ---

    @Test
    void testFairnessAndFatigueReport() throws InterruptedException {
        // Arrange: Create 3 threads.
        // Due to Math.random(), we don't know which one has the best fatigue factor,
        // but we know they all start at 0 fatigue.
        TiredExecutor executor = new TiredExecutor(3);
        int totalTasks = 100; 

        // Act: Submit a large number of tasks (100).
        // Since fatigue accumulates, the "Least Tired" worker keeps changing.
        // The executor should eventually rotate through ALL workers.
        for (int i = 0; i < totalTasks; i++) {
             executor.submit(() -> {
                 try { 
                     // Tiny sleep to ensure timeUsed increases
                     Thread.sleep(1); 
                 } catch (InterruptedException e) {}
             });
        }

        // Wait ample time for all tasks to finish
        Thread.sleep(1000);

        // Get the report to inspect the internal state
        String report = executor.getWorkerReport();
        
        // Print it to console so you can see the distribution with your own eyes
        System.out.println(report);

        // Assert: 
        // We verify "Fairness" by checking that NO worker remained idle (Time Used: 0).
        // In the report format: "Time Used: 0 " implies a worker did nothing.
        // Note: We search for "Time Used: 0 " with a trailing space to avoid matching numbers like "10" or "205".
        assertFalse(report.contains("Time Used: 0 "), 
            "Distribution failure: All workers should have participated in the work. " +
            "If a worker has 0 time used, the priority queue is likely ignoring them.");

        executor.shutdown();
    }

    // --- Part 6: Lifecycle & Shutdown ---

    @Test
    void testShutdown() {
        // Arrange: Executor with active workers
        TiredExecutor executor = new TiredExecutor(3);

        // Act 1: Submit a few tasks to ensure threads are alive and working
        for (int i = 0; i < 10; i++) {
            executor.submit(() -> {
                try {
                    Thread.sleep(10);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
        }

        // Act 2 & Assert: Call shutdown.
        // This method calls 'join()' on all workers. 
        // If a worker is stuck or doesn't process the poison pill, this line will hang forever.
        // If it returns without exception, the shutdown was successful.
        assertDoesNotThrow(() -> executor.shutdown(), 
            "Shutdown should complete successfully without throwing exceptions or hanging");
    }
}