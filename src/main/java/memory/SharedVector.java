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

        if (other == null) {
            throw new IllegalArgumentException("error: Illegal operation: null vector");
        }

        SharedVector first = this;
        SharedVector second = other;
        boolean thisFirst = true;

        if (System.identityHashCode(first) > System.identityHashCode(second)) {
            first = other;
            second = this;
            thisFirst = false;
        }

        if (thisFirst) {
            first.writeLock();   // this
            second.readLock();   // other
        } else {
            first.readLock();    // other
            second.writeLock();  // this
        }

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
            if (thisFirst) {
                second.readUnlock();
                first.writeUnlock();
            } else {
                second.writeUnlock();
                first.readUnlock();
            }
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

        if (other == null) {
            throw new IllegalArgumentException("error: Illegal operation: null vector");
        }

        SharedVector first = this;
        SharedVector second = other;
        
        if (System.identityHashCode(first) > System.identityHashCode(second)) {
            first = other;
            second = this;
        }

        first.readLock();
        second.readLock();  

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
            second.readUnlock();
            first.readUnlock(); 
        }
        
    }

    public void vecMatMul(SharedMatrix matrix) {
        // TODO: compute row-vector × matrix

        if (matrix == null || matrix.length() == 0) return;

        VectorOrientation matOri = matrix.getOrientation();       
     

        double[] tempVec;
        this.readLock();
        try {

            if (this.orientation != VectorOrientation.ROW_MAJOR) {
                throw new IllegalArgumentException("error: Illegal operation: orientations mismatch");
            }
            
            int thisLen = this.length();        
            int matSize = matrix.length();

            if (matOri == VectorOrientation.ROW_MAJOR) {
                if (thisLen != matSize) { 
                    throw new IllegalArgumentException("error: dimensions mismatch for ROW_MAJOR matrix");
                }
                int resLen = matrix.get(0).length(); 
                tempVec = new double[resLen];

                double[] myData = this.vector;
                
                for (int i = 0; i < matSize; i++) {
                    double scalar = myData[i];

                    SharedVector row = matrix.get(i);
                    row.readLock();
                    try {
                        for (int j = 0; j < resLen; j++) {
                            tempVec[j] += scalar * row.vector[j];
                        }
                    } finally {
                        row.readUnlock();
                    }
                }
            } else {
                if (thisLen != matrix.get(0).length()) {
                 throw new IllegalArgumentException("error: dimensions mismatch for COLUMN_MAJOR matrix");
                }

                int resLen = matSize; 
                tempVec = new double[resLen];
                double[] myData = this.vector;

                for (int j = 0; j < resLen; j++) {
                    SharedVector col = matrix.get(j);
                    
                    col.readLock();
                    try {
                        for (int k = 0; k < thisLen; k++) {
                            tempVec[j] += myData[k] * col.vector[k];
                        }
                    } finally {
                        col.readUnlock();
                    }
                }
            }
        } finally {
            this.readUnlock();
        }

        this.writeLock();
        try {
            this.vector = tempVec;
        } finally {
            this.writeUnlock();
        }
    }
}
