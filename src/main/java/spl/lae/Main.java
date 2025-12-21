package spl.lae;
import java.io.IOException;

import parser.*;

public class Main {
    public static void main(String[] args) throws IOException {
      // TODO: main

      // Read arguments
      int numThreads = Integer.parseInt(args[0]);
      String inputFilePath = args[1];
      String outputFilePath = args[2];

      try {
          // Parse computation tree
          InputParser inputParser = new InputParser();
          ComputationNode computationRoot = inputParser.parse(inputFilePath);
          computationRoot.associativeNesting();

          LinearAlgebraEngine lae = new LinearAlgebraEngine(numThreads);
          lae.run(computationRoot);
          System.out.println(lae.getWorkerReport());

          // Write output
          OutputWriter.write(computationRoot.getMatrix(), outputFilePath);

      } catch (Exception e) {
          OutputWriter.write(e.getMessage(), outputFilePath);
          return;
      }
    }
}