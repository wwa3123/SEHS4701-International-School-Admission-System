package hk.edu.sehs4701.cissas.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.jdbc.core.JdbcTemplate;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class DevDemoDataSeederTest {
    @Configuration(proxyBeanMethods = false)
    static class ConfigOnlyApplication {}

    @Test
    void seedsOnlyMissingDemoAcademicYearAndLevels() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);

        new DevDemoDataSeeder(jdbc).run(new DefaultApplicationArguments());

        verify(jdbc).update(contains("INSERT INTO academic_year"), any(), any(), any());
        verify(jdbc, times(3)).update(contains("INSERT INTO programme_level"),
                any(), any(), any(), any(), any(), any());
        verifyNoMoreInteractions(jdbc);
    }

    @Test
    void copiedExampleLoadsWithTheDevProfile(@TempDir Path directory) throws Exception {
        try (InputStream example = getClass().getResourceAsStream("/application-dev.properties.example")) {
            assertNotNull(example);
            Files.copy(example, directory.resolve("application-dev.properties"));
        }

        StandardEnvironment environment = new StandardEnvironment();
        environment.getPropertySources().remove(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME);
        environment.getPropertySources().remove(StandardEnvironment.SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME);
        try (var context = new SpringApplicationBuilder(ConfigOnlyApplication.class)
                .environment(environment)
                .web(WebApplicationType.NONE)
                .logStartupInfo(false)
                .run("--spring.config.location=" + directory.toUri(),
                        "--spring.profiles.active=dev",
                        "--SPRING_DATASOURCE_USERNAME=local-user",
                        "--SPRING_DATASOURCE_PASSWORD=example",
                        "--IDENTITY_DOCUMENT_SECRET=example",
                        "--IDENTITY_DOCUMENT_SALT=0123456789abcdef",
                        "--IDENTITY_DOCUMENT_HASH_PEPPER=example",
                        "--OTC_CODE_HASH_PEPPER=example",
                        "--CISSAS_SUPER_ADMIN_EMAIL=admin@example.test",
                        "--CISSAS_SUPER_ADMIN_FIRST_NAME=Local",
                        "--CISSAS_SUPER_ADMIN_LAST_NAME=Admin",
                        "--CISSAS_SUPER_ADMIN_PASSWORD=example")) {
            assertEquals("validate", context.getEnvironment().getProperty("spring.jpa.hibernate.ddl-auto"));
            assertEquals("local-user", context.getEnvironment().getProperty("spring.datasource.username"));
            assertEquals("example", context.getEnvironment().getProperty("spring.datasource.password"));
            assertEquals("example", context.getEnvironment().getProperty("identity-document.encryption.secret"));
            assertEquals("0123456789abcdef", context.getEnvironment().getProperty("identity-document.encryption.salt"));
            assertEquals("admin@example.test", context.getEnvironment().getProperty("cissas.super-admin.email"));
        }
    }
}
