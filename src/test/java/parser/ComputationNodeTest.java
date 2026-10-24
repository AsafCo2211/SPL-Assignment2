package parser;

import org.junit.jupiter.api.Test;
import spl.lae.LinearAlgebraEngine;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ComputationNodeTest {

    private static ComputationNode matrix(double value) {
        return new ComputationNode(new double[][] {{value}});
    }

    private static ComputationNode op(ComputationNodeType type, ComputationNode... operands) {
        return new ComputationNode(type, new ArrayList<>(List.of(operands)));
    }

    @Test
    void nestingReachesOperatorsBelowTheRoot() {
        // Regression: -(1 + 10 + 100). Only the root used to be nested, so the
        // inner three-operand "+" kept three children, the engine read the
        // first two, and the result was -11.
        ComputationNode root = op(ComputationNodeType.NEGATE,
                op(ComputationNodeType.ADD, matrix(1), matrix(10), matrix(100)));

        root.associativeNesting();
        new LinearAlgebraEngine(2).run(root);

        assertArrayEquals(new double[] {-111}, root.getMatrix()[0]);
    }

    @Test
    void everyNodeHasAtMostTwoOperandsAfterNesting() {
        ComputationNode root = op(ComputationNodeType.MULTIPLY,
                op(ComputationNodeType.ADD, matrix(1), matrix(2), matrix(3), matrix(4)),
                op(ComputationNodeType.MULTIPLY, matrix(5), matrix(6), matrix(7)));

        root.associativeNesting();

        assertAllBinary(root);
    }

    private static void assertAllBinary(ComputationNode node) {
        if (node.getNodeType() == ComputationNodeType.MATRIX) return;
        assertTrue(node.getChildren().size() <= 2,
                node.getNodeType() + " has " + node.getChildren().size() + " operands");
        node.getChildren().forEach(ComputationNodeTest::assertAllBinary);
    }
}
