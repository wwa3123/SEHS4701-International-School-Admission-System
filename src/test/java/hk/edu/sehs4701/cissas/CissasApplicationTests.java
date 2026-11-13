package hk.edu.sehs4701.cissas;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import static org.junit.jupiter.api.Assertions.assertFalse;

@SpringBootTest(properties = {
        "CISSAS_SUPER_ADMIN_EMAIL=test-super@example.com",
        "CISSAS_SUPER_ADMIN_FIRST_NAME=Test",
        "CISSAS_SUPER_ADMIN_LAST_NAME=Admin",
        "CISSAS_SUPER_ADMIN_PASSWORD=test-password"
})
@EnabledIfEnvironmentVariable(named = "CISSAS_TEST_DATABASE_URL", matches = ".+")
class CissasApplicationTests extends DisposableDatabase {
    @Autowired
    @Qualifier("requestMappingHandlerMapping")
    private RequestMappingHandlerMapping mappings;

    @Test
    void contextLoads() {
        mappings.getHandlerMethods().forEach((mapping, handler) -> {
            if (handler.getBeanType().getPackageName().startsWith("hk.edu.sehs4701.cissas")) {
                assertFalse(handler.getBeanType().isAnnotationPresent(
                        org.springframework.web.bind.annotation.RestController.class));
                assertFalse(handler.hasMethodAnnotation(org.springframework.web.bind.annotation.ResponseBody.class));
                assertFalse(mapping.getPatternValues().stream().anyMatch(path -> path.startsWith("/api/")),
                        "JSON API route still registered: " + mapping);
            }
        });
    }

}
