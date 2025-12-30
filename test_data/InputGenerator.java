package spl.lae;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import parser.ComputationNodeType;

import java.io.File;
import java.io.IOException;
import java.util.Random;

public class InputGenerator {

    private static final Random random = new Random();
    private static final ObjectMapper mapper =
            new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

    // Test configuration
    private static final int MATRIX_SIZE = 100; // 100x100 matrices
    private static final int TREE_DEPTH = 5;    // Computation tree depth (recursion depth)

    public static void main(String[] args) throws IOException {
        System.out.println("Generating random computation tree...");

        // 1. Generate a random computation tree (data structure only)
        GeneratedNode root = generateTree(TREE_DEPTH);

        // 2. Save the generated input as JSON (so the engine can read it)
        File inputFile = new File("big_input.json");
        mapper.writeValue(inputFile, root.toJsonNode());
        System.out.println("Input saved to: " + inputFile.getAbsolutePath());

        // 3. Compute the expected result sequentially (reference implementation)
        System.out.println("Calculating expected result (sequentially)...");
        double[][] expectedMatrix = solveSequentially(root);

        // 4. Save the expected output as JSON
        // Wrapped in the same structure produced by OutputWriter
        File expectedFile = new File("expected_output.json");
        ObjectNode resultJson = mapper.createObjectNode();
        resultJson.putPOJO("result", expectedMatrix);
        mapper.writeValue(expectedFile, resultJson);
        System.out.println("Expected output saved to: " + expectedFile.getAbsolutePath());
    }

    // --- Internal helper structures for representing the tree before serialization ---

    interface GeneratedNode {
        com.fasterxml.jackson.databind.JsonNode toJsonNode();
        double[][] compute(); // Computes this node's result (reference computation)
    }

    static class MatrixNode implements GeneratedNode {
        double[][] data;

        public MatrixNode(double[][] data) {
            this.data = data;
        }

        @Override
        public com.fasterxml.jackson.databind.JsonNode toJsonNode() {
            ArrayNode outer = mapper.createArrayNode();
            for (double[] row : data) {
                ArrayNode inner = mapper.createArrayNode();
                for (double val : row) {
                    inner.add(val);
                }
                outer.add(inner);
            }
            return outer;
        }

        @Override
        public double[][] compute() {
            return data;
        }
    }

    static class OpNode implements GeneratedNode {
        String operator;
        GeneratedNode[] children;

        public OpNode(String operator, GeneratedNode... children) {
            this.operator = operator;
            this.children = children;
        }

        @Override
        public com.fasterxml.jackson.databind.JsonNode toJsonNode() {
            ObjectNode node = mapper.createObjectNode();
            node.put("operator", operator);
            ArrayNode operands = mapper.createArrayNode();
            for (GeneratedNode child : children) {
                operands.add(child.toJsonNode());
            }
            node.set("operands", operands);
            return node;
        }

        @Override
        public double[][] compute() {
            // Simple sequential reference logic
            double[][] res = children[0].compute();

            if (operator.equals("T")) return transpose(res);
            if (operator.equals("-")) return negate(res);

            // Binary operations (generator guarantees exactly two children here)
            double[][] other = children[1].compute();
            if (operator.equals("+")) return add(res, other);
            if (operator.equals("*")) return multiply(res, other);

            throw new RuntimeException("Unknown operator");
        }
    }

    // --- Computation tree generation logic ---

    private static GeneratedNode generateTree(int depth) {
        // Base case: at depth 0, or with 20% probability, return a matrix leaf
        if (depth == 0 || random.nextDouble() < 0.2) {
            return new MatrixNode(generateRandomMatrix());
        }

        // Randomly choose an operation
        // 0: Add, 1: Multiply, 2: Negate, 3: Transpose
        int action = random.nextInt(4);

        if (action == 0) { // ADD
            return new OpNode("+",
                    generateTree(depth - 1),
                    generateTree(depth - 1));
        } else if (action == 1) { // MULTIPLY
            return new OpNode("*",
                    generateTree(depth - 1),
                    generateTree(depth - 1));
        } else if (action == 2) { // NEGATE
            return new OpNode("-",
                    generateTree(depth - 1));
        } else { // TRANSPOSE
            return new OpNode("T",
                    generateTree(depth - 1));
        }
    }

    private static double[][] generateRandomMatrix() {
        double[][] m = new double[MATRIX_SIZE][MATRIX_SIZE];
        for (int i = 0; i < MATRIX_SIZE; i++) {
            for (int j = 0; j < MATRIX_SIZE; j++) {
                m[i][j] = Math.random() * 10;
            }
        }
        return m;
    }

    // --- Naive sequential math operations (reference implementation) ---

    private static double[][] solveSequentially(GeneratedNode root) {
        return root.compute();
    }

    private static double[][] add(double[][] a, double[][] b) {
        int n = a.length;
        double[][] res = new double[n][n];
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                res[i][j] = a[i][j] + b[i][j];
            }
        }
        return res;
    }

    private static double[][] negate(double[][] a) {
        int n = a.length;
        double[][] res = new double[n][n];
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                res[i][j] = -a[i][j];
            }
        }
        return res;
    }

    private static double[][] transpose(double[][] a) {
        int n = a.length;
        double[][] res = new double[n][n];
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                res[i][j] = a[j][i];
            }
        }
        return res;
    }

    private static double[][] multiply(double[][] a, double[][] b) {
        int n = a.length;
        double[][] res = new double[n][n];
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                double sum = 0;
                for (int k = 0; k < n; k++) {
                    sum += a[i][k] * b[k][j];
                }
                res[i][j] = sum;
            }
        }
        return res;
    }
}
