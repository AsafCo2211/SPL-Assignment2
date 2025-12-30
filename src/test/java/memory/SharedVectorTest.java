package memory;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class SharedVectorTest {

    // --- A. Constructor & Basic Access (Tests 1-7) ---

    @Test // 1
    public void ctor_copiesInputArray_notAliasing() {
        double[] input = {1.0, 2.0, 3.0};
        SharedVector v = new SharedVector(input, VectorOrientation.ROW_MAJOR);
        
        // Modify external array
        input[0] = 99.0;
        
        // Internal vector should remain unchanged
        assertEquals(1.0, v.get(0), "Constructor must copy the input array, not store a reference.");
    }

    @Test // 2
    public void ctor_nullVector_throws() {
        assertThrows(IllegalArgumentException.class, () -> {
            new SharedVector(null, VectorOrientation.ROW_MAJOR);
        }, "Should throw exception when vector data is null.");
    }

    @Test // 3
    public void ctor_nullOrientation_throws() {
        double[] input = {1.0, 2.0};
        assertThrows(IllegalArgumentException.class, () -> {
            new SharedVector(input, null);
        }, "Should throw exception when orientation is null.");
    }

    @Test // 4
    public void get_validIndices_returnsCorrect() {
        double[] input = {10.0, 20.0, 30.0};
        SharedVector v = new SharedVector(input, VectorOrientation.ROW_MAJOR);
        
        assertEquals(10.0, v.get(0));
        assertEquals(20.0, v.get(1));
        assertEquals(30.0, v.get(2));
    }

    @Test // 5
    public void get_outOfBounds_throws() {
        double[] input = {1.0, 2.0};
        SharedVector v = new SharedVector(input, VectorOrientation.ROW_MAJOR);
        
        assertThrows(IndexOutOfBoundsException.class, () -> v.get(-1));
        assertThrows(IndexOutOfBoundsException.class, () -> v.get(2));
    }

    @Test // 6
    public void length_returnsCorrect() {
        double[] input = {1.0, 2.0, 3.0, 4.0};
        SharedVector v = new SharedVector(input, VectorOrientation.ROW_MAJOR);
        assertEquals(4, v.length());
    }

    @Test // 7
    public void getOrientation_returnsCorrect() {
        SharedVector vRow = new SharedVector(new double[]{1}, VectorOrientation.ROW_MAJOR);
        assertEquals(VectorOrientation.ROW_MAJOR, vRow.getOrientation());

        SharedVector vCol = new SharedVector(new double[]{1}, VectorOrientation.COLUMN_MAJOR);
        assertEquals(VectorOrientation.COLUMN_MAJOR, vCol.getOrientation());
    }

    // --- B. Transpose & Negate (Tests 8-11) ---

    @Test // 8
    public void transpose_togglesOrientation() {
        SharedVector v = new SharedVector(new double[]{1, 2}, VectorOrientation.ROW_MAJOR);
        v.transpose();
        assertEquals(VectorOrientation.COLUMN_MAJOR, v.getOrientation());
        
        v.transpose();
        assertEquals(VectorOrientation.ROW_MAJOR, v.getOrientation());
    }

    @Test // 9
    public void transpose_twice_returnsOriginalOrientation() {
        SharedVector v = new SharedVector(new double[]{1, 2}, VectorOrientation.ROW_MAJOR);
        VectorOrientation original = v.getOrientation();
        
        v.transpose();
        v.transpose();
        
        assertEquals(original, v.getOrientation(), "Transpose twice should return to original orientation.");
    }

    @Test // 10
    public void negate_flipsSignsCorrectly() {
        SharedVector v = new SharedVector(new double[]{1, -2, 0}, VectorOrientation.ROW_MAJOR);
        v.negate();
        
        assertEquals(-1.0, v.get(0));
        assertEquals(2.0, v.get(1));
        assertEquals(-0.0, v.get(2));
    }

    @Test // 11
    public void negate_twice_returnsOriginalVector() {
        double[] input = {1.5, -3.5};
        SharedVector v = new SharedVector(input, VectorOrientation.ROW_MAJOR);
        
        v.negate();
        v.negate();
        
        assertEquals(1.5, v.get(0));
        assertEquals(-3.5, v.get(1));
    }

    // --- C. Add (Tests 12-15) ---

    @Test // 12
    public void add_sameDimSameOrientation_correctResult() {
        SharedVector v1 = new SharedVector(new double[]{1, 2}, VectorOrientation.ROW_MAJOR);
        SharedVector v2 = new SharedVector(new double[]{3, 4}, VectorOrientation.ROW_MAJOR);
        
        v1.add(v2);
        
        // v1 (left) is modified
        assertEquals(4.0, v1.get(0));
        assertEquals(6.0, v1.get(1));
        
        // v2 (right) is unchanged
        assertEquals(3.0, v2.get(0));
    }

    @Test // 13
    public void add_nullOther_throws() {
        SharedVector v = new SharedVector(new double[]{1}, VectorOrientation.ROW_MAJOR);
        assertThrows(IllegalArgumentException.class, () -> v.add(null));
    }

    @Test // 14
    public void add_dimMismatch_throws() {
        SharedVector v1 = new SharedVector(new double[]{1}, VectorOrientation.ROW_MAJOR);
        SharedVector v2 = new SharedVector(new double[]{1, 2}, VectorOrientation.ROW_MAJOR);
        
        assertThrows(IllegalArgumentException.class, () -> v1.add(v2));
    }

    @Test // 15
    public void add_orientationMismatch_throws() {
        SharedVector v1 = new SharedVector(new double[]{1, 2}, VectorOrientation.ROW_MAJOR);
        SharedVector v2 = new SharedVector(new double[]{1, 2}, VectorOrientation.COLUMN_MAJOR);
        
        assertThrows(IllegalArgumentException.class, () -> v1.add(v2));
    }

    // --- D. Dot Product (Tests 16-19) ---

    @Test // 16
    public void dot_rowWithCol_correctScalar() {
        SharedVector row = new SharedVector(new double[]{1, 2}, VectorOrientation.ROW_MAJOR);
        SharedVector col = new SharedVector(new double[]{3, 4}, VectorOrientation.COLUMN_MAJOR);
        
        double result = row.dot(col);
        // 1*3 + 2*4 = 11
        assertEquals(11.0, result, 0.0001);
    }

    @Test // 17
    public void dot_nullOther_throws() {
        SharedVector v = new SharedVector(new double[]{1}, VectorOrientation.ROW_MAJOR);
        assertThrows(IllegalArgumentException.class, () -> v.dot(null));
    }

    @Test // 18
    public void dot_dimMismatch_throws() {
        SharedVector row = new SharedVector(new double[]{1}, VectorOrientation.ROW_MAJOR);
        SharedVector col = new SharedVector(new double[]{1, 2}, VectorOrientation.COLUMN_MAJOR);
        
        assertThrows(IllegalArgumentException.class, () -> row.dot(col));
    }

    @Test // 19
    public void dot_sameOrientation_throws() {
        SharedVector v1 = new SharedVector(new double[]{1, 2}, VectorOrientation.ROW_MAJOR);
        SharedVector v2 = new SharedVector(new double[]{3, 4}, VectorOrientation.ROW_MAJOR);
        
        // Dot product is only defined between row and column in this implementation
        assertThrows(IllegalArgumentException.class, () -> v1.dot(v2));
    }

    // --- E. vecMatMul (Tests 20-26) ---

    @Test // 20
    public void vecMatMul_rowMajorMatrix_correctResult() {
        // Vector: [1, 2] (Row)
        SharedVector v = new SharedVector(new double[]{1, 2}, VectorOrientation.ROW_MAJOR);
        
        // Matrix (Row Major):
        // [3, 4]
        // [5, 6]
        // Result: [1*3 + 2*5, 1*4 + 2*6] = [3+10, 4+12] = [13, 16]
        SharedMatrix m = new SharedMatrix();
        m.loadRowMajor(new double[][]{{3, 4}, {5, 6}});
        
        v.vecMatMul(m);
        
        assertEquals(13.0, v.get(0), 0.0001);
        assertEquals(16.0, v.get(1), 0.0001);
    }

    @Test // 21
    public void vecMatMul_columnMajorMatrix_correctResult() {
        // Same calculation, but matrix stored as Column Major
        SharedVector v = new SharedVector(new double[]{1, 2}, VectorOrientation.ROW_MAJOR);
        
        SharedMatrix m = new SharedMatrix();
        m.loadColumnMajor(new double[][]{{3, 4}, {5, 6}});
        
        v.vecMatMul(m);
        
        assertEquals(13.0, v.get(0), 0.0001);
        assertEquals(16.0, v.get(1), 0.0001);
    }

    @Test // 22
    public void vecMatMul_vectorNotRowMajor_throws() {
        SharedVector v = new SharedVector(new double[]{1, 2}, VectorOrientation.COLUMN_MAJOR);
        SharedMatrix m = new SharedMatrix();
        m.loadRowMajor(new double[][]{{1, 1}, {1, 1}});
        
        // Vector-Matrix multiplication is defined for Row Vector * Matrix
        assertThrows(IllegalArgumentException.class, () -> v.vecMatMul(m));
    }

    @Test // 23
    public void vecMatMul_dimMismatch_rowMajor_throws() {
        SharedVector v = new SharedVector(new double[]{1, 2, 3}, VectorOrientation.ROW_MAJOR); // Length 3
        SharedMatrix m = new SharedMatrix();
        m.loadRowMajor(new double[][]{{1, 1}, {1, 1}}); // 2 Rows
        
        // Vector length must match matrix rows
        assertThrows(IllegalArgumentException.class, () -> v.vecMatMul(m));
    }

    @Test // 24
    public void vecMatMul_dimMismatch_columnMajor_throws() {
        SharedVector v = new SharedVector(new double[]{1, 2, 3}, VectorOrientation.ROW_MAJOR); // Length 3
        SharedMatrix m = new SharedMatrix();
        m.loadColumnMajor(new double[][]{{1, 1}, {1, 1}}); // 2 Rows
        
        assertThrows(IllegalArgumentException.class, () -> v.vecMatMul(m));
    }

    @Test // 25
    public void vecMatMul_nullMatrix_returnsWithoutChange() {
        SharedVector v = new SharedVector(new double[]{1, 2}, VectorOrientation.ROW_MAJOR);
        v.vecMatMul(null);
        
        // Should adhere to contract: no change, no crash
        assertEquals(1.0, v.get(0));
        assertEquals(2.0, v.get(1));
    }

    @Test // 26
    public void vecMatMul_emptyMatrix_returnsWithoutChange() {
        SharedVector v = new SharedVector(new double[]{1, 2}, VectorOrientation.ROW_MAJOR);
        SharedMatrix m = new SharedMatrix(); // Empty
        
        v.vecMatMul(m);
        
        assertEquals(1.0, v.get(0));
        assertEquals(2.0, v.get(1));
    }
}