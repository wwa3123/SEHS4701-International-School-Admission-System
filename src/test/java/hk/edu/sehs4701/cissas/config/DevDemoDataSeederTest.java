package hk.edu.sehs4701.cissas.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class DevDemoDataSeederTest {
    @Test
    void seedsOnlyMissingDemoAcademicYearAndLevels() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);

        new DevDemoDataSeeder(jdbc).run(new DefaultApplicationArguments());

        verify(jdbc).update(contains("INSERT INTO academic_year"), any(), any(), any());
        verify(jdbc, times(3)).update(contains("INSERT INTO programme_level"),
                any(), any(), any(), any(), any(), any());
        verifyNoMoreInteractions(jdbc);
    }
}
