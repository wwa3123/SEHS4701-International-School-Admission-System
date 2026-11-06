package hk.edu.sehs4701.cissas;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

public abstract class DisposableDatabase {
    @DynamicPropertySource
    static void configureDatabase(DynamicPropertyRegistry registry) {
        String url = requireDisposableDatabase(System.getenv("CISSAS_TEST_DATABASE_URL"));
        registry.add("spring.datasource.url", () -> url + "?createDatabaseIfNotExist=true");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
        registry.add("spring.jpa.show-sql", () -> "false");
        registry.add("identity-document.encryption.secret", () -> "test-only-secret");
        registry.add("identity-document.encryption.salt", () -> "0123456789abcdef");
        registry.add("identity-document.hash.pepper", () -> "test-only-pepper");
        registry.add("otc.code.hash.pepper", () -> "test-only-pepper");
    }

    static String requireDisposableDatabase(String url) {
        if (url == null || !url.matches("jdbc:mysql://localhost:3306/cissas_test_[a-z0-9]+")) {
            throw new IllegalStateException(
                    "CISSAS_TEST_DATABASE_URL must point to a disposable jdbc:mysql://localhost:3306/cissas_test_<name> schema");
        }
        return url;
    }
}
