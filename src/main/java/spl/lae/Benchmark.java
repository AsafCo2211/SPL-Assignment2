package spl.lae;

import parser.ComputationNode;
import parser.ComputationNodeType;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

/**
 * Measures how matrix multiplication scales with the number of worker threads.
 *
 *   java -cp target/parallel-matrix-engine-1.0.jar spl.lae.Benchmark [size] [repeats] [threads...]
 *
 * Multiplies two random size×size matrices (fixed seed) with each thread
 * count, reports the median wall-clock time of the engine run, and checks that
 * every thread count produced exactly the same result as the first.
 */
public final class Benchmark {

    public static void main(String[] args) {
        int size = args.length > 0 ? Integer.parseInt(args[0]) : 500;
        int repeats = args.length > 1 ? Integer.parseInt(args[1]) : 5;
        int[] threadCounts = args.length > 2
                ? Arrays.stream(args, 2, args.length).mapToInt(Integer::parseInt).toArray()
                : new int[] {1, 2, 4, 8};

        Random rng = new Random(42);
        double[][] a = randomMatrix(size, rng);
        double[][] b = randomMatrix(size, rng);

        System.out.printf("Multiplying two %dx%d matrices, median of %d runs, %d CPU cores available%n%n",
                size, size, repeats, Runtime.getRuntime().availableProcessors());
        System.out.println("| threads | median time | speed-up |");
        System.out.println("|--------:|------------:|---------:|");

        runOnce(a, b, threadCounts[0]);   // warm up the JIT before timing anything

        double baseline = 0;
        double[][] reference = null;
        for (int threads : threadCounts) {
            long[] times = new long[repeats];
            double[][] result = null;
            for (int r = 0; r < repeats; r++) {
                long start = System.nanoTime();
                result = runOnce(a, b, threads);
                times[r] = System.nanoTime() - start;
            }
            Arrays.sort(times);
            double medianMs = times[repeats / 2] / 1e6;

            if (reference == null) {
                reference = result;
                baseline = medianMs;
            } else if (!Arrays.deepEquals(reference, result)) {
                throw new IllegalStateException(threads + " threads produced a different result");
            }
            System.out.printf("| %7d | %8.1f ms | %7.2fx |%n", threads, medianMs, baseline / medianMs);
        }
    }

    private static double[][] runOnce(double[][] a, double[][] b, int threads) {
        List<ComputationNode> operands = new ArrayList<>();
        operands.add(new ComputationNode(copy(a)));
        operands.add(new ComputationNode(copy(b)));
        ComputationNode root = new ComputationNode(ComputationNodeType.MULTIPLY, operands);
        new LinearAlgebraEngine(threads).run(root);
        return root.getMatrix();
    }

    private static double[][] randomMatrix(int n, Random rng) {
        double[][] m = new double[n][n];
        for (double[] row : m)
            for (int j = 0; j < n; j++) row[j] = rng.nextDouble() * 2 - 1;
        return m;
    }

    private static double[][] copy(double[][] m) {
        double[][] c = new double[m.length][];
        for (int i = 0; i < m.length; i++) c[i] = m[i].clone();
        return c;
    }
}
