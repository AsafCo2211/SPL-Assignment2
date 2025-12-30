package spl.lae;

import parser.*;
import memory.*;
import scheduling.*;

import java.util.LinkedList;
import java.util.List;

public class LinearAlgebraEngine {

    private SharedMatrix leftMatrix = new SharedMatrix();
    private SharedMatrix rightMatrix = new SharedMatrix();
    private TiredExecutor executor;

    public LinearAlgebraEngine(int numThreads) {
        // TODO: create executor with given thread count
        this.executor = new TiredExecutor(numThreads);
    }

    public ComputationNode run(ComputationNode computationRoot) {
        // TODO: resolve computation tree step by step until final matrix is produced
        if (computationRoot == null) {
            throw new NullPointerException("Computation node cannot be null");
        }

        try {
        ComputationNode resolvableNode = computationRoot.findResolvable();
        while (resolvableNode != null) {
            loadAndCompute(resolvableNode);
            resolvableNode.resolve(leftMatrix.readRowMajor());
            resolvableNode = computationRoot.findResolvable();
        }
        return computationRoot;

        } finally {
            try {
                executor.shutdown();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    public void loadAndCompute(ComputationNode node) {
        // TODO: load operand matrices
        // TODO: create compute tasks & submit tasks to executor

        // load operand matrices

        if (node == null) {
            throw new NullPointerException("Computation node cannot be null");
        }

        if ((node.getNodeType() == ComputationNodeType.ADD || node.getNodeType() == ComputationNodeType.MULTIPLY)
            && node.getChildren().size() != 2) {
            throw new IllegalArgumentException("Binary operation node must have exactly 2 children.");
        }
        if ((node.getNodeType() == ComputationNodeType.NEGATE || node.getNodeType() == ComputationNodeType.TRANSPOSE)
            && node.getChildren().size() != 1) {
            throw new IllegalArgumentException("Unary operation node must have exactly 1 child.");
        }

        List<ComputationNode> children = node.getChildren();
        leftMatrix.loadRowMajor(children.get(0).getMatrix());
        if (node.getNodeType() == ComputationNodeType.ADD) {
            rightMatrix.loadRowMajor(children.get(1).getMatrix());
        }
        else if (node.getNodeType() == ComputationNodeType.MULTIPLY) {
            rightMatrix.loadColumnMajor(children.get(1).getMatrix());
        }

        // create tasks & submit to executor
        List<Runnable> tasks = switch (node.getNodeType()) {
            case ADD -> createAddTasks();
            case MULTIPLY -> createMultiplyTasks();
            case NEGATE -> createNegateTasks();
            case TRANSPOSE -> createTransposeTasks();
            default -> throw new IllegalArgumentException("Unsupported operation: " + node.getNodeType());
        };
        executor.submitAll(tasks);

    }

    public List<Runnable> createAddTasks() {
        // TODO: return tasks that perform row-wise addition
        
        if (leftMatrix.length() != rightMatrix.length()) {
            throw new IllegalArgumentException("Illegal operation: dimensions mismatch");
        }

        List<Runnable> tasks = new java.util.LinkedList<>();

        for (int i = 0; i < leftMatrix.length(); i++) {
            SharedVector leftRow = leftMatrix.get(i);
            SharedVector rightRow = rightMatrix.get(i);

            if (leftRow.length() != rightRow.length()) {
                throw new IllegalArgumentException("Illegal operation: dimensions mismatch");
            }

            tasks.add(() -> {
                leftRow.add(rightRow);
            });
        }

        return tasks;
    }

    public List<Runnable> createMultiplyTasks() {
        // TODO: return tasks that perform row × matrix multiplication
        boolean isMatix1Empty = leftMatrix.length() == 0 || leftMatrix.get(0).length() == 0;
        boolean isMatix2Empty = rightMatrix.length() == 0 || rightMatrix.get(0).length() == 0;
        if (isMatix1Empty && isMatix2Empty) {
            // both matrices are empty, multiplication is empty.
            return new LinkedList<>();
        }
        if (isMatix1Empty || isMatix2Empty) {
            // one of the matrices is empty, multiplication is invalid.
            throw new IllegalArgumentException("Illegal operation: dimensions mismatch");
        }

        if (leftMatrix.getOrientation() != VectorOrientation.ROW_MAJOR || rightMatrix.getOrientation() != VectorOrientation.COLUMN_MAJOR) {
            throw new IllegalArgumentException("Illegal operation: orientations mismatch");
        }

        if (leftMatrix.get(0).length() != rightMatrix.get(0).length()) {
            throw new IllegalArgumentException("Illegal operation: dimensions mismatch: "
                + leftMatrix.get(0).length() + " != " + rightMatrix.get(0).length());
        }

        
        List<Runnable> tasks = new java.util.LinkedList<>();
        for (int i = 0; i < leftMatrix.length(); i++) {
            SharedVector leftRow = leftMatrix.get(i);

            tasks.add(() -> {
                leftRow.vecMatMul(rightMatrix);
            });
        }
        return tasks;
    }

    public List<Runnable> createNegateTasks() {
        // TODO: return tasks that negate rows
        List<Runnable> tasks = new java.util.LinkedList<>();
        for (int i = 0; i < leftMatrix.length(); i++) {
            SharedVector leftRow = leftMatrix.get(i);

            tasks.add(() -> {
                leftRow.negate();
            });
        }
        return tasks;
    }

    public List<Runnable> createTransposeTasks() {
        // TODO: return tasks that transpose rows
        List<Runnable> tasks = new java.util.LinkedList<>();
        for (int i = 0; i < leftMatrix.length(); i++) {
            SharedVector leftRow = leftMatrix.get(i);

            tasks.add(() -> {
                leftRow.transpose();
            });
        }

        return tasks;
    }

    public String getWorkerReport() {
        // TODO: return summary of worker activity
        return executor.getWorkerReport();
    }
}