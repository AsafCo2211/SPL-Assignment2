package spl.lae;

import parser.*;
import memory.*;
import scheduling.*;

import java.util.List;

public class LinearAlgebraEngine {

    private SharedMatrix leftMatrix = new SharedMatrix();
    private SharedMatrix rightMatrix = new SharedMatrix();
    private TiredExecutor executor;

    public LinearAlgebraEngine(int numThreads) {
        this.executor = new TiredExecutor(numThreads);
    }

    public ComputationNode run(ComputationNode computationRoot) {

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

        // load operand matrices
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
        
        if (leftMatrix.length() != rightMatrix.length()) {
            throw new IllegalArgumentException("Matrices have different number of rows for addition.");
        }

        List<Runnable> tasks = new java.util.LinkedList<>();

        for (int i = 0; i < leftMatrix.length(); i++) {
            SharedVector leftRow = leftMatrix.get(i);
            SharedVector rightRow = rightMatrix.get(i);

            tasks.add(() -> {
                leftRow.add(rightRow);
            });
        }

        return tasks;
    }

    public List<Runnable> createMultiplyTasks() {
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
        return executor.getWorkerReport();
    }
}