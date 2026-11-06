package hk.edu.sehs4701.cissas;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
@EnabledIfEnvironmentVariable(named = "CISSAS_TEST_DATABASE_URL", matches = ".+")
class CissasApplicationTests extends DisposableDatabase {

    @Test
    void contextLoads() {
    }

}
