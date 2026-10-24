package spl.lae;

import org.junit.jupiter.api.Test;
import parser.ComputationNode;
import parser.ComputationNodeType;

import java.time.Duration;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

class LinearAlgebraEngineTest {

    // --- Helpers ---

    private double[][] generateRandomMatrix(int rows, int cols) {
        double[][] matrix = new double[rows][cols];
        for (int i = 0; i < rows; i++) {
            for (int j = 0; j < cols; j++) {
                matrix[i][j] = Math.random() * 10;
            }
        }
        return matrix;
    }

    private ComputationNode createLeafNode(double[][] data) {
        return new ComputationNode(data);
    }

    private ComputationNode createOpNode(ComputationNodeType type, ComputationNode... children) {
        return new ComputationNode(type, Arrays.asList(children));
    }

    // --- Tests ---

    @Test
    void testInitializationAndReport() {
        int numThreads = 2;
        LinearAlgebraEngine engine = new LinearAlgebraEngine(numThreads);
        String report = engine.getWorkerReport();

        assertNotNull(report);
        assertTrue(report.contains("Worker #0"));
        assertTrue(report.contains("Worker #1"));
    }

    @Test
    void testLargeMatrixAddition() {
        assertTimeoutPreemptively(Duration.ofSeconds(10), () -> {
            int size = 100; // 100x100 matrix
            LinearAlgebraEngine engine = new LinearAlgebraEngine(4);
            double[][] matA = generateRandomMatrix(size, size);
            double[][] matB = generateRandomMatrix(size, size);

            ComputationNode leafA = createLeafNode(matA);
            ComputationNode leafB = createLeafNode(matB);
            ComputationNode root = createOpNode(ComputationNodeType.ADD, leafA, leafB);

            ComputationNode resultNode = engine.run(root);
            double[][] resultMatrix = resultNode.getMatrix();

            assertEquals(size, resultMatrix.length);
            for (int i = 0; i < size; i++) {
                for (int j = 0; j < size; j++) {
                    assertEquals(matA[i][j] + matB[i][j], resultMatrix[i][j], 0.0001);
                }
            }
        });
    }

    @Test
    void testAdditionDimensionMismatch() {
        // Same number of rows, different number of columns: the mismatch is
        // only detected inside the row tasks. It used to be swallowed by the
        // workers and run() returned the unchanged left matrix as a result.
        LinearAlgebraEngine engine = new LinearAlgebraEngine(2);
        double[][] matA = generateRandomMatrix(2, 2);
        double[][] matB = generateRandomMatrix(2, 3); // Mismatch!

        ComputationNode leafA = createLeafNode(matA);
        ComputationNode leafB = createLeafNode(matB);
        ComputationNode root = createOpNode(ComputationNodeType.ADD, leafA, leafB);

        assertThrows(IllegalArgumentException.class, () -> engine.run(root));
    }

    @Test
    void testLargeMatrixMultiplication() {
        assertTimeoutPreemptively(Duration.ofSeconds(10), () -> {
            int rowsA = 50;
            int common = 30;
            int colsB = 40;

            LinearAlgebraEngine engine = new LinearAlgebraEngine(4);
            double[][] matA = generateRandomMatrix(rowsA, common);
            double[][] matB = generateRandomMatrix(common, colsB);

            ComputationNode leafA = createLeafNode(matA);
            ComputationNode leafB = createLeafNode(matB);
            ComputationNode root = createOpNode(ComputationNodeType.MULTIPLY, leafA, leafB);

            ComputationNode resultNode = engine.run(root);
            double[][] resultMatrix = resultNode.getMatrix();

            assertEquals(rowsA, resultMatrix.length);
            assertEquals(colsB, resultMatrix[0].length);

            // Check sample points
            for (int i = 0; i < rowsA; i += 5) {
                for (int j = 0; j < colsB; j += 5) {
                    double expectedSum = 0;
                    for (int k = 0; k < common; k++) {
                        expectedSum += matA[i][k] * matB[k][j];
                    }
                    assertEquals(expectedSum, resultMatrix[i][j], 0.0001, "Mismatch at " + i + "," + j);
                }
            }
        });
    }

    @Test
    void testUnaryOperations() {
        assertTimeoutPreemptively(Duration.ofSeconds(5), () -> {
            int size = 20;
            double[][] matA = generateRandomMatrix(size, size);

            // 1. Test NEGATE
            // Create a FRESH engine for this run
            LinearAlgebraEngine engineNeg = new LinearAlgebraEngine(2);
            ComputationNode leafA = createLeafNode(matA);
            ComputationNode rootNeg = createOpNode(ComputationNodeType.NEGATE, leafA);
            
            double[][] resNeg = engineNeg.run(rootNeg).getMatrix();

            for (int i = 0; i < size; i++) {
                for (int j = 0; j < size; j++) {
                    assertEquals(-matA[i][j], resNeg[i][j], 0.0001);
                }
            }

            // 2. Test TRANSPOSE
            // CRITICAL FIX: Create a NEW engine because the previous one shut down!
            LinearAlgebraEngine engineTrans = new LinearAlgebraEngine(2);
            ComputationNode leafB = createLeafNode(matA);
            ComputationNode rootTrans = createOpNode(ComputationNodeType.TRANSPOSE, leafB);
            
            double[][] resTrans = engineTrans.run(rootTrans).getMatrix();

            for (int i = 0; i < size; i++) {
                for (int j = 0; j < size; j++) {
                    assertEquals(matA[j][i], resTrans[i][j], 0.0001);
                }
            }
        });
    }

    // --- Part 3: Complex Integration & Multi-Step Expressions ---

    /**
     * Test 1: Linear Chain Expression -> (A + B) * C
     * Focus: Resolving dependencies sequentially.
     * The engine must calculate (A+B), save the result, reload it as LeftMatrix, and then multiply by C.
     */
    @Test
    void testComplexChainExpression() {
        assertTimeoutPreemptively(Duration.ofSeconds(10), () -> {
            int size = 30;
            LinearAlgebraEngine engine = new LinearAlgebraEngine(4);

            // Generate matrices
            double[][] matA = generateRandomMatrix(size, size);
            double[][] matB = generateRandomMatrix(size, size);
            double[][] matC = generateRandomMatrix(size, size);

            // Build Tree: (A + B) * C
            ComputationNode nodeA = createLeafNode(matA);
            ComputationNode nodeB = createLeafNode(matB);
            ComputationNode nodeC = createLeafNode(matC);

            ComputationNode addNode = createOpNode(ComputationNodeType.ADD, nodeA, nodeB);
            ComputationNode rootMul = createOpNode(ComputationNodeType.MULTIPLY, addNode, nodeC);

            // Execute
            ComputationNode resultNode = engine.run(rootMul);
            double[][] resultMatrix = resultNode.getMatrix();

            // Verify Logic manually
            // 1. Expected Intermediate = A + B
            double[][] intermediate = new double[size][size];
            for(int i=0; i<size; i++) {
                for(int j=0; j<size; j++) {
                    intermediate[i][j] = matA[i][j] + matB[i][j];
                }
            }

            // 2. Expected Final = Intermediate * C
            for (int i = 0; i < size; i++) {
                for (int j = 0; j < size; j++) {
                    double expectedSum = 0;
                    for (int k = 0; k < size; k++) {
                        expectedSum += intermediate[i][k] * matC[k][j];
                    }
                    assertEquals(expectedSum, resultMatrix[i][j], 0.001, 
                        "Mismatch in Chain Expression (A+B)*C at [" + i + "][" + j + "]");
                }
            }
        });
    }

    /**
     * Test 2: Two Branches Tree -> (A + B) + (C + D)
     * Focus: Handling multiple resolvable nodes.
     * The engine might solve the left branch first, or the right branch first.
     * This ensures the tree traversal (findResolvable) doesn't get stuck on one side.
     */
    @Test
    void testBalancedTreeExpression() {
        assertTimeoutPreemptively(Duration.ofSeconds(10), () -> {
            int size = 50;
            LinearAlgebraEngine engine = new LinearAlgebraEngine(4);

            double[][] matA = generateRandomMatrix(size, size);
            double[][] matB = generateRandomMatrix(size, size);
            double[][] matC = generateRandomMatrix(size, size);
            double[][] matD = generateRandomMatrix(size, size);

            // Left Branch: A + B
            ComputationNode branch1 = createOpNode(ComputationNodeType.ADD, 
                                        createLeafNode(matA), createLeafNode(matB));

            // Right Branch: C + D
            ComputationNode branch2 = createOpNode(ComputationNodeType.ADD, 
                                        createLeafNode(matC), createLeafNode(matD));

            // Root: Branch1 + Branch2
            ComputationNode root = createOpNode(ComputationNodeType.ADD, branch1, branch2);

            // Execute
            double[][] result = engine.run(root).getMatrix();

            // Verify: Result should be A + B + C + D
            for (int i = 0; i < size; i++) {
                for (int j = 0; j < size; j++) {
                    double expected = matA[i][j] + matB[i][j] + matC[i][j] + matD[i][j];
                    assertEquals(expected, result[i][j], 0.001, 
                        "Mismatch in Balanced Tree (A+B)+(C+D)");
                }
            }
        });
    }

    /**
     * Test 3: Dimension Changing Chain -> Transpose(A) * B
     * Focus: Ensuring intermediate dimensions are updated correctly.
     * A is 20x10. Transpose(A) becomes 10x20. B is 20x5. Result should be 10x5.
     * If the engine uses A's original dimensions for the multiplication step, this will crash.
     */
    @Test
    void testTransposeThenMultiply() {
        assertTimeoutPreemptively(Duration.ofSeconds(10), () -> {
            int rowsA = 20;
            int colsA = 10; // So Transpose(A) is 10x20
            int colsB = 5;

            LinearAlgebraEngine engine = new LinearAlgebraEngine(4);

            double[][] matA = generateRandomMatrix(rowsA, colsA);
            double[][] matB = generateRandomMatrix(colsA, colsB); // 10x5 Wait... No.
            // Logic check:
            // Transpose(A) is (colsA x rowsA) -> (10 x 20).
            // To multiply (10x20) * B, B must have 20 rows.
            // So B should be (rowsA x colsB) -> (20 x 5).
            
            // Correcting B dimensions for the test logic:
            double[][] matB_Corrected = generateRandomMatrix(rowsA, colsB); 

            // Build Tree: Transpose(A) * B
            ComputationNode nodeA = createLeafNode(matA);
            ComputationNode nodeB = createLeafNode(matB_Corrected);

            ComputationNode transNode = createOpNode(ComputationNodeType.TRANSPOSE, nodeA);
            ComputationNode rootMul = createOpNode(ComputationNodeType.MULTIPLY, transNode, nodeB);

            // Execute
            ComputationNode resultNode = engine.run(rootMul);
            double[][] resultMatrix = resultNode.getMatrix();

            // Assert Dimensions: Result should be (colsA x colsB) -> (10 x 5)
            assertEquals(colsA, resultMatrix.length);
            assertEquals(colsB, resultMatrix[0].length);

            // Verify Values
            // Manual calc: A^T * B
            for (int i = 0; i < colsA; i++) { // 10 rows
                for (int j = 0; j < colsB; j++) { // 5 cols
                    double sum = 0;
                    for (int k = 0; k < rowsA; k++) { // Common dim is 20
                        // Note the index swap for A: [k][i] instead of [i][k]
                        sum += matA[k][i] * matB_Corrected[k][j];
                    }
                    assertEquals(sum, resultMatrix[i][j], 0.001, "Mismatch in Transpose*Multiply");
                }
            }
        });
    }

    @Test
    void testAutomaticShutdown() {
        // Step 1: Initialize the engine and a simple task.
        // This forces the creation of worker threads.
        LinearAlgebraEngine engine = new LinearAlgebraEngine(2);
        double[][] data = {{1.0}};
        ComputationNode root = createLeafNode(data);

        // Step 2: Run the task.
        // The 'finally' block inside engine.run() is expected to call shutdown(),
        // which in turn calls join() on all worker threads.
        engine.run(root);

        // Step 3: Verify that workers are dead (without using Reflection).
        // We retrieve all currently active threads in the JVM.
        java.util.Set<Thread> allThreads = Thread.getAllStackTraces().keySet();

        // Iterate over all threads to find any lingering "TiredThread".
        for (Thread t : allThreads) {
            // Check if the thread is alive and belongs to our worker class.
            if (t.isAlive() && t.getClass().getSimpleName().equals("TiredThread")) {
                
                // If we find such a thread, it means the shutdown failed.
                fail("Test Failed: Found a TiredThread that is still running after shutdown! Name: " + t.getName());
            }
        }
    }
}