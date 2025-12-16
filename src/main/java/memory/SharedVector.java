package memory;


import java.util.concurrent.locks.ReadWriteLock;

public class SharedVector {

    private double[] vector;
    private VectorOrientation orientation;
    private ReadWriteLock lock = new java.util.concurrent.locks.ReentrantReadWriteLock();

    public SharedVector(double[] vector, VectorOrientation orientation) {
        // TODO: store vector data and its orientation
        this.vector = vector;
        this.orientation = orientation; 
    }

    public double get(int index) {
        // TODO: return element at index (read-locked)
        this.readLock();
        try {
            return this.vector[index];
        } finally {
            this.readUnlock();
        }
    }

    public int length() {
        // TODO: return vector length

         this.readLock();
        try {
            return this.vector.length;  
        } finally {
            this.readUnlock();
        }
    }

    public VectorOrientation getOrientation() {
        // TODO: return vector orientation
        
        this.readLock();
        try {
            return this.orientation;  
        } finally {
            this.readUnlock();
        }
    }

    public void writeLock() {
        // TODO: acquire write lock

        lock.writeLock().lock();    
    }

    public void writeUnlock() {
        // TODO: release write lock

        lock.writeLock().unlock();
    }

    public void readLock() {
        // TODO: acquire read lock

        lock.readLock().lock();
    }

    public void readUnlock() {
        // TODO: release read lock

        lock.readLock().unlock();
    }

    public void transpose() {
        // TODO: transpose vector

        this.writeLock();
        try {
            if (this.orientation == VectorOrientation.ROW_MAJOR) {
                this.orientation = VectorOrientation.COLUMN_MAJOR;
            } else {
                this.orientation = VectorOrientation.ROW_MAJOR;
            }
        } finally {
            this.writeUnlock();
        }
    }

    public void add(SharedVector other) {
        // TODO: add two vectors

        this.writeLock();
        other.readLock();

        try {
            if(this.length() != other.length()) {
                throw new IllegalArgumentException("error: Illegal operation: dimensions mismatch");
            }
            if(this.orientation != other.orientation) {
                throw new IllegalArgumentException("error: Illegal operation: orientations mismatch");
            }

            for(int i=0; i < this.length(); i++){
                this.vector[i] += other.vector[i];
            }
        } finally {
            other.readUnlock();
            this.writeUnlock();
        }
    }

    public void negate() {
        // TODO: negate vector

        this.writeLock();
        try {
            for(int i=0; i < this.length(); i++){
                this.vector[i] = -this.vector[i];
            }
        } finally {
            this.writeUnlock();
        }
    }

    public double dot(SharedVector other) {
        // TODO: compute dot product (row · column)

        this.readLock();
        other.readLock();  

        try {
            if(this.length() != other.length()) {
                throw new IllegalArgumentException("error: Illegal operation: dimensions mismatch");
            }
            if(this.orientation == other.orientation) {
                throw new IllegalArgumentException("error: Illegal operation: orientations mismatch");
            }

            double result = 0.0;
            for(int i=0; i < this.length(); i++){
                result += this.vector[i] * other.vector[i];
            }
            return result;
        } finally {
            other.readUnlock();
            this.readUnlock();
        }
        
    }

    public void vecMatMul(SharedMatrix matrix) {
        // TODO: compute row-vector × matrix

        if (matrix == null || matrix.length() == 0) return;

        final int n = matrix.length();
        final int m = matrix.get(0).length();

        final double[] thisVec;
        this.readLock();
        try {
            if (this.length() != n) {
                throw new IllegalArgumentException("error: Illegal operation: dimensions mismatch");
            }
            if (this.orientation != VectorOrientation.ROW_MAJOR) {
                throw new IllegalArgumentException("error: Illegal operation: orientations mismatch");
            }
            
            thisVec = this.vector.clone();
        } finally {
            this.readUnlock();
        }

        double[] tempVec = new double[m];
        for(int i = 0; i < n; i++) {
            SharedVector row = matrix.get(i);
            row.readLock();
            try {
                for(int j = 0; j < m; j++) {
                    tempVec[j] += thisVec[i] * row.vector[j];
                }
            } finally {
                row.readUnlock();
            }
        }
        
        this.writeLock();
        try {
            this.vector = tempVec;
        } finally {
            this.writeUnlock();
        }
    }
}
