package memory;

public class SharedMatrix {

    private volatile SharedVector[] vectors = {}; // underlying vectors

    public SharedMatrix() {
        // TODO: initialize empty matrix

        this.vectors = new SharedVector[0];
    }

    public SharedMatrix(double[][] matrix) {
        // TODO: construct matrix as row-major SharedVectors

        if (matrix == null) {
            this.vectors = new SharedVector[0];
        } else {
            this.vectors = new SharedVector[matrix.length];
            for (int i = 0; i < matrix.length; i++) {
                this.vectors[i] = new SharedVector(matrix[i], VectorOrientation.ROW_MAJOR);
            }
        }
    }

    public void loadRowMajor(double[][] matrix) {
        // TODO: replace internal data with new row-major matrix

        if (matrix == null || matrix.length == 0) {
            this.vectors = new SharedVector[0];
        } else {
            SharedVector[] newVectors = new SharedVector[matrix.length];
            for (int i = 0; i < matrix.length; i++) {
                newVectors[i] = new SharedVector(matrix[i], VectorOrientation.ROW_MAJOR);
            }
            this.vectors = newVectors;
        }
    }

    public void loadColumnMajor(double[][] matrix) {
        // TODO: replace internal data with new column-major matrix

        if (matrix == null || matrix.length == 0) {
            this.vectors = new SharedVector[0];
        } else {
            SharedVector[] newVectors = new SharedVector[matrix[0].length];
            for (int j = 0; j < matrix[0].length; j++) {
                double[] row = new double[matrix.length];
                for (int i = 0; i < matrix.length; i++) {
                    row[i] = matrix[i][j];
                }
                newVectors[j] = new SharedVector(row, VectorOrientation.COLUMN_MAJOR);
            }
            this.vectors = newVectors;
        }  
    }

    public double[][] readRowMajor() {
        // TODO: return matrix contents as a row-major double[][]

        SharedVector[] currentVecs = this.vectors;
             
        if (currentVecs.length == 0) {
            return new double[0][0];
        }

        VectorOrientation orientation = currentVecs[0].getOrientation();   

        acquireAllVectorReadLocks(currentVecs);
        try {
            int n = currentVecs.length; // number of vectors
            int m = currentVecs[0].length(); // length of each vector

            if (orientation == VectorOrientation.ROW_MAJOR) {
                double[][] result = new double[n][m];
                for (int i = 0; i < n; i++) {
                    for (int j = 0; j < m; j++) {
                        result[i][j] = currentVecs[i].get(j);
                    }
                }
                return result;
            } else {
                double[][] result = new double[m][n];
                for (int j = 0; j < n; j++) {
                    for (int i = 0; i < m; i++) {
                        result[i][j] = currentVecs[j].get(i);
                    }
                }
                return result;
            }
        } finally {
            releaseAllVectorReadLocks(currentVecs);
        }
    }

    public SharedVector get(int index) {
        // TODO: return vector at index

        SharedVector[] refs = this.vectors;
        
        if (index < 0 || index >= refs.length) {
            throw new IndexOutOfBoundsException("error: Index out of bounds");
        }
        return refs[index];
    }

    public int length() {
        // TODO: return number of stored vectors

        return this.vectors.length;
    }

    public VectorOrientation getOrientation() {
        // TODO: return orientation
        
        SharedVector[] refs = this.vectors;

        if (refs.length == 0) {
            throw new IllegalStateException("error: Matrix is empty");
        }
        return refs[0].getOrientation();
    }

    private void acquireAllVectorReadLocks(SharedVector[] vecs) {
        // TODO: acquire read lock for each vector
        for (SharedVector vec : vecs) {
            vec.readLock();
        }
    }

    private void releaseAllVectorReadLocks(SharedVector[] vecs) {
        // TODO: release read locks
        for (SharedVector vec : vecs) {
            vec.readUnlock();
        }
    }

    private void acquireAllVectorWriteLocks(SharedVector[] vecs) {
        // TODO: acquire write lock for each vector
        for (SharedVector vec : vecs) {
            vec.writeLock();
        }
    }

    private void releaseAllVectorWriteLocks(SharedVector[] vecs) {
        // TODO: release write locks
        for (SharedVector vec : vecs) {
            vec.writeUnlock();
        }
    }
}
