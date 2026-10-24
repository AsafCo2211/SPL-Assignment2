package memory;


import java.util.concurrent.locks.ReadWriteLock;

public class SharedVector {

    private double[] vector;
    private VectorOrientation orientation;
    private ReadWriteLock lock = new java.util.concurrent.locks.ReentrantReadWriteLock();

    public SharedVector(double[] vector, VectorOrientation orientation) {

        if (vector == null) {
        throw new IllegalArgumentException("error: vector cannot be null");
        }
        if (orientation == null) {
            throw new IllegalArgumentException("error: orientation cannot be null");
        }

        this.vector = new double[vector.length];

        for (int i = 0; i < vector.length; i++) {
            this.vector[i] = vector[i];
        }

        this.orientation = orientation; 
    }

    public double get(int index) {
        this.readLock();
        try {
            if (index < 0 || index >= this.vector.length) {
                throw new IndexOutOfBoundsException("error: Index out of bounds");
            }   
            return this.vector[index];
        } finally {
            this.readUnlock();
        }
    }

    public int length() {

         this.readLock();
        try {
            return this.vector.length;  
        } finally {
            this.readUnlock();
        }
    }

    public VectorOrientation getOrientation() {
        
        this.readLock();
        try {
            return this.orientation;  
        } finally {
            this.readUnlock();
        }
    }

    public void writeLock() {

        lock.writeLock().lock();    
    }

    public void writeUnlock() {

        lock.writeLock().unlock();
    }

    public void readLock() {

        lock.readLock().lock();
    }

    public void readUnlock() {

        lock.readLock().unlock();
    }

    public void transpose() {

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

        if (matrix == null || matrix.length() == 0) return;

        try {
            if (matrix.get(0).length() == 0) return;
        } catch (Exception e) {
            throw new IllegalArgumentException("error: matrix internal structure is invalid");
        }

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
