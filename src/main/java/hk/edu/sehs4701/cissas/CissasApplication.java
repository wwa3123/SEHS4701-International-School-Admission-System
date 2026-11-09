package hk.edu.sehs4701.cissas;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import java.util.TimeZone;
import java.util.Arrays;

@SpringBootApplication
@EnableAsync
@EnableRetry
@EnableScheduling
public class CissasApplication {
    public static void main(String[] args) {
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Hong_Kong"));
        if (Arrays.asList(args).contains("--recover-super-admin")) {
            SpringApplication application = new SpringApplication(CissasApplication.class);
            application.setWebApplicationType(WebApplicationType.NONE);
            application.run(args).close();
        } else {
            SpringApplication.run(CissasApplication.class, args);
        }
    }
}
