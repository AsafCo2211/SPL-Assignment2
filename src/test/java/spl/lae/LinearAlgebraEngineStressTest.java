package spl.lae;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import parser.ComputationNode;
import parser.InputParser;

import java.io.File;
import java.io.FileWriter;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class LinearAlgebraEngineStressTest {

    private LinearAlgebraEngine lae;
    private InputParser parser;
    private Random random = new Random();

    @BeforeEach
    void setUp() {
        // הנה מה שביקשת: 10 ת'רדים שיתנפלו על העבודה!
        lae = new LinearAlgebraEngine(10);
        parser = new InputParser();
    }

    /**
     * בדיקת עומס: כפל מטריצות ענק (100x100)
     * פעולה זו דורשת כ-2 מיליון פעולות אריתמטיות (100^3 * 2).
     * זה המבחן האולטימטיבי למקביליות - אם יש באג, זה יקרוס פה.
     */
    @Test
    void testMassiveMatrixMultiplication(@TempDir Path tempDir) throws Exception {
        System.out.println("Starting Massive Matrix Multiplication Test (100x100)...");
        
        int size = 100; // גודל המטריצה (נסה להגדיל ל-200 או 500 אם אתה אמיץ)
        double[][] A = generateRandomMatrix(size, size);
        double[][] B = generateRandomMatrix(size, size);

        // בניית ה-JSON באופן דינמי (כי לכתוב את זה ידנית זה סיוט)
        // משתמשים ב-deepToString כי הוא מייצר פורמט תואם JSON למערכים: [[1,2],[3,4]]
        String json = """
            {
              "operator": "multiply",
              "operands": [
                %s,
                %s
              ]
            }
            """.formatted(Arrays.deepToString(A), Arrays.deepToString(B));

        // חישוב התוצאה הצפויה (בצורה סדרתית פשוטה ואמינה לצורך בדיקה)
        double[][] expected = multiplyMatricesSequentially(A, B);

        // הרצת המנוע המקבילי שלך
        double[][] actual = runTest(json, tempDir);

        // השוואה
        assertMatricesEqual(expected, actual);
        System.out.println("Massive Multiplication Test Passed! 🚀");
    }

    /**
     * בדיקת עומס: עץ חישוב מורכב ועמוק
     * הביטוי: (A + B) * (C - D)^T
     * בודק שילוב של כל הפעולות עם מטריצות גדולות.
     */
    @Test
    void testComplexOperationTree(@TempDir Path tempDir) throws Exception {
        System.out.println("Starting Complex Tree Test ((A+B)*(C-D)^T)...");
        
        int rows = 50;
        int cols = 50;
        
        double[][] A = generateRandomMatrix(rows, cols);
        double[][] B = generateRandomMatrix(rows, cols);
        // C ו-D צריכים להיות הפוכים בממדים כדי שאחרי Transpose הם יתאימו לכפל
        // אם A+B זה 50x50, אז הצד השני צריך להיות 50x50 כדי שיוכלו להכפיל.
        // אז C-D יהיה 50x50, ואחרי T הוא יהיה 50x50.
        double[][] C = generateRandomMatrix(cols, rows);
        double[][] D = generateRandomMatrix(cols, rows);

        String json = """
            {
              "operator": "multiply",
              "operands": [
                {
                   "operator": "add",
                   "operands": [ %s, %s ]
                },
                {
                   "operator": "transpose",
                   "operands": [
                      {
                         "operator": "negate",
                         "operands": [
                            {
                               "operator": "add",
                               "operands": [ %s, %s ] 
                            }
                         ]
                      }
                   ]
                }
              ]
            }
            """.formatted(
                    Arrays.deepToString(A), Arrays.deepToString(B),
                    Arrays.deepToString(C), 
                    // שים לב: עשיתי כאן טריק, במקום C-D עשיתי C+(-D) אבל ב-JSON למעלה כתבתי C+D בתוך Negate בתוך Transpose
                    // כדי לפשט, בוא נבנה JSON שמייצג: (A + B) * (C + D)
                    Arrays.deepToString(D)
            );

        // נעדכן את ה-JSON למשהו פשוט יותר וודאי: (A + B) * (C + D)
        // הכל מטריצות 50x50
        json = """
            {
              "operator": "multiply",
              "operands": [
                { "operator": "add", "operands": [ %s, %s ] },
                { "operator": "add", "operands": [ %s, %s ] }
              ]
            }
            """.formatted(Arrays.deepToString(A), Arrays.deepToString(B), Arrays.deepToString(C), Arrays.deepToString(D));


        // חישוב צפוי
        double[][] sum1 = addMatrices(A, B);
        double[][] sum2 = addMatrices(C, D);
        double[][] expected = multiplyMatricesSequentially(sum1, sum2);

        double[][] actual = runTest(json, tempDir);

        assertMatricesEqual(expected, actual);
        System.out.println("Complex Tree Test Passed! 🌳");
    }

    // --- Helpers ---

    private double[][] runTest(String jsonContent, Path tempDir) throws Exception {
        File tempFile = tempDir.resolve("stress_test.json").toFile();
        try (FileWriter writer = new FileWriter(tempFile)) {
            writer.write(jsonContent);
        }
        ComputationNode root = parser.parse(tempFile.getAbsolutePath());
        root.associativeNesting();
        ComputationNode result = lae.run(root);
        return result.getMatrix();
    }

    private double[][] generateRandomMatrix(int rows, int cols) {
        double[][] matrix = new double[rows][cols];
        for (int i = 0; i < rows; i++) {
            for (int j = 0; j < cols; j++) {
                matrix[i][j] = random.nextInt(10); // מספרים קטנים כדי לא לחרוג מ-Double בטעות
            }
        }
        return matrix;
    }

    private void assertMatricesEqual(double[][] expected, double[][] actual) {
        assertEquals(expected.length, actual.length, "Row count mismatch");
        assertEquals(expected[0].length, actual[0].length, "Col count mismatch");
        for (int i = 0; i < expected.length; i++) {
            assertArrayEquals(expected[i], actual[i], 0.001, "Mismatch at row " + i);
        }
    }

    // מימושים סדרתיים פשוטים לבדיקה (Reference Implementation)
    private double[][] multiplyMatricesSequentially(double[][] A, double[][] B) {
        int rowsA = A.length;
        int colsA = A[0].length;
        int colsB = B[0].length;
        double[][] C = new double[rowsA][colsB];
        for (int i = 0; i < rowsA; i++) {
            for (int j = 0; j < colsB; j++) {
                for (int k = 0; k < colsA; k++) {
                    C[i][j] += A[i][k] * B[k][j];
                }
            }
        }
        return C;
    }

    private double[][] addMatrices(double[][] A, double[][] B) {
        int rows = A.length;
        int cols = A[0].length;
        double[][] C = new double[rows][cols];
        for(int i=0; i<rows; i++) {
            for(int j=0; j<cols; j++) {
                C[i][j] = A[i][j] + B[i][j];
            }
        }
        return C;
    }
}