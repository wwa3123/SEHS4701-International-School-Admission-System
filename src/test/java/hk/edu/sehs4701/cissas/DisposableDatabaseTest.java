package hk.edu.sehs4701.cissas;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DisposableDatabaseTest {
    @Test
    void acceptsOnlyExplicitDisposableLocalSchemas() {
        String safe = "jdbc:mysql://localhost:3306/cissas_test_example";
        assertEquals(safe, DisposableDatabase.requireDisposableDatabase(safe));
        for (String unsafe : new String[] {
                null,
                "jdbc:mysql://localhost:3306/cissas",
                "jdbc:mysql://remote:3306/cissas_test_example",
                "jdbc:mysql://localhost:3306/cissas_test_example?database=cissas",
                "jdbc:mysql://localhost:3306/cissas_test_"
        }) {
            assertThrows(IllegalStateException.class, () -> DisposableDatabase.requireDisposableDatabase(unsafe));
        }
    }
}
