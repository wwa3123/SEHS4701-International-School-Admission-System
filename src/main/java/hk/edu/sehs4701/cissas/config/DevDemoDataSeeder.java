package hk.edu.sehs4701.cissas.config;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Component
@Profile("dev")
public class DevDemoDataSeeder implements ApplicationRunner {
    private static final ZoneId HONG_KONG = ZoneId.of("Asia/Hong_Kong");
    private static final DateTimeFormatter YEAR_FORMAT = DateTimeFormatter.ofPattern("yyyy");
    private final JdbcTemplate jdbc;

    public DevDemoDataSeeder(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        LocalDate today = LocalDate.now(HONG_KONG);
        LocalDate start = LocalDate.of(today.getYear(), 9, 1);
        if (today.isBefore(start)) start = start.minusYears(1);
        String yearName = "DEMO " + start.format(YEAR_FORMAT) + "/"
                + start.plusYears(1).format(YEAR_FORMAT);
        LocalDate applicationStart = today.minusMonths(1);
        LocalDate applicationEnd = today.plusMonths(3);

        jdbc.update("""
                INSERT INTO academic_year (name, creation_date, status)
                SELECT ?, ?, 'ACTIVE'
                WHERE NOT EXISTS (SELECT 1 FROM academic_year WHERE name = ?)
                """, yearName, start, yearName);

        for (DemoLevel level : List.of(
                new DemoLevel("S1", "Secondary 1"),
                new DemoLevel("S2", "Secondary 2"),
                new DemoLevel("S3", "Secondary 3"))) {
            jdbc.update("""
                    INSERT INTO programme_level
                        (level_code, programme_name, academic_year_id, seat_quota, admission_open,
                         application_start_date, application_end_date, updated_at)
                    SELECT ?, ?, id, 30, TRUE, ?, ?, CURRENT_TIMESTAMP
                    FROM academic_year ay
                    WHERE ay.name = ?
                      AND NOT EXISTS (
                          SELECT 1 FROM programme_level p
                          WHERE p.academic_year_id = ay.id AND p.level_code = ?
                      )
                    """, level.code(), level.name(), applicationStart, applicationEnd, yearName, level.code());
        }
    }

    private record DemoLevel(String code, String name) {}
}
