package hk.edu.sehs4701.cissas;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
        "CISSAS_SUPER_ADMIN_EMAIL=test-super@example.com",
        "CISSAS_SUPER_ADMIN_FIRST_NAME=Test",
        "CISSAS_SUPER_ADMIN_LAST_NAME=Admin",
        "CISSAS_SUPER_ADMIN_PASSWORD=test-password"
})
@EnabledIfEnvironmentVariable(named = "CISSAS_TEST_DATABASE_URL", matches = ".+")
class CissasApplicationTests extends DisposableDatabase {

    @Test
    void contextLoads() {
    }

}
