package memory;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class SharedMatrixTest {

    // --- A. Constructors & Empty State (Tests 1-3) ---

    @Test // 1
    public void ctor_default_isEmpty_length0() {
        SharedMatrix m = new SharedMatrix();
        assertEquals(0, m.length(), "Default constructor should create an empty matrix with length 0.");
    }

    @Test // 2
    public void ctor_nullMatrix_createsEmpty() {
        SharedMatrix m = new SharedMatrix(null);
        assertEquals(0, m.length(), "Constructor with null input should create an empty matrix safely.");
    }

    @Test // 3
    public void ctor_fromMatrix_rowMajorVectorsCreatedCorrectly() {
        double[][] data = {{1, 2}, {3, 4}};
        SharedMatrix m = new SharedMatrix(data);
        
        assertEquals(2, m.length());
        assertEquals(VectorOrientation.ROW_MAJOR, m.getOrientation());
        
        // Verify content matches original
        assertEquals(1.0, m.get(0).get(0));
        assertEquals(3.0, m.get(1).get(0));
    }

    // --- B. loadRowMajor (Tests 4-6) ---

    @Test // 4
    public void loadRowMajor_valid_roundTripWithReadRowMajor() {
        double[][] data = {{1, 2, 3}, {4, 5, 6}};
        SharedMatrix m = new SharedMatrix();
        m.loadRowMajor(data);
        
        double[][] result = m.readRowMajor();
        
        assertEquals(data.length, result.length);
        assertEquals(data[0].length, result[0].length);
        assertArrayEquals(data[0], result[0]);
        assertArrayEquals(data[1], result[1]);
    }

    @Test // 5
    public void loadRowMajor_null_resultsEmpty() {
        SharedMatrix m = new SharedMatrix(new double[][]{{1, 2}});
        m.loadRowMajor(null);
        assertEquals(0, m.length(), "loadRowMajor(null) should reset matrix to empty.");
    }

    @Test // 6
    public void loadRowMajor_empty_resultsEmpty() {
        SharedMatrix m = new SharedMatrix(new double[][]{{1, 2}});
        m.loadRowMajor(new double[0][0]);
        assertEquals(0, m.length(), "loadRowMajor(empty) should reset matrix to empty.");
    }

    // --- C. loadColumnMajor (Tests 7-10) ---

    @Test // 7
    public void loadColumnMajor_valid_internalVectorsRepresentColumns() {
        // Data:
        // 1 2
        // 3 4
        // If loaded as ColumnMajor, internal vectors should be columns: [1, 3] and [2, 4]
        double[][] data = {{1, 2}, {3, 4}};
        SharedMatrix m = new SharedMatrix();
        m.loadColumnMajor(data);
        
        assertEquals(VectorOrientation.COLUMN_MAJOR, m.getOrientation());
        
        // Check internal vector 0 (should be first column)
        SharedVector col0 = m.get(0);
        assertEquals(1.0, col0.get(0));
        assertEquals(3.0, col0.get(1));
    }

    @Test // 8
    public void loadColumnMajor_valid_readRowMajorReconstructsOriginal() {
        // Critical requirement: even if stored as ColumnMajor, readRowMajor must return normal matrix
        double[][] data = {{1, 2, 3}, {4, 5, 6}};
        SharedMatrix m = new SharedMatrix();
        m.loadColumnMajor(data);
        
        double[][] result = m.readRowMajor();
        
        assertArrayEquals(data[0], result[0]);
        assertArrayEquals(data[1], result[1]);
    }

    @Test // 9
    public void loadColumnMajor_null_resultsEmpty() {
        SharedMatrix m = new SharedMatrix();
        m.loadColumnMajor(null);
        assertEquals(0, m.length());
    }

    @Test // 10
    public void loadColumnMajor_empty_resultsEmpty() {
        SharedMatrix m = new SharedMatrix();
        m.loadColumnMajor(new double[0][0]);
        assertEquals(0, m.length());
    }

    // --- D. API & Error Handling (Tests 11-14) ---

    @Test // 11
    public void get_validIndex_returnsVectorReference() {
        double[][] data = {{10, 20}};
        SharedMatrix m = new SharedMatrix(data);
        SharedVector v = m.get(0);
        assertNotNull(v);
        assertEquals(10.0, v.get(0));
    }

    @Test // 12
    public void get_outOfBounds_throws() {
        SharedMatrix m = new SharedMatrix(new double[][]{{1}});
        assertThrows(IndexOutOfBoundsException.class, () -> m.get(-1));
        assertThrows(IndexOutOfBoundsException.class, () -> m.get(1));
    }

    @Test // 13
    public void getOrientation_onEmpty_throwsIllegalState() {
        SharedMatrix m = new SharedMatrix(); // Empty
        // Cannot determine orientation if there are no vectors
        assertThrows(IllegalStateException.class, m::getOrientation);
    }

    @Test // 14
    public void readRowMajor_onEmpty_returns00() {
        SharedMatrix m = new SharedMatrix();
        double[][] res = m.readRowMajor();
        assertNotNull(res);
        assertEquals(0, res.length);
    }
}