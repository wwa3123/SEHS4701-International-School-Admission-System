package hk.edu.sehs4701.cissas.service;

import hk.edu.sehs4701.cissas.entity.Application;
import hk.edu.sehs4701.cissas.repository.ApplicationRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Slice;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneId;

@Slf4j
@Component
public class OfferReminderScheduler {
    private static final ZoneId HONG_KONG = ZoneId.of("Asia/Hong_Kong");

    private final ApplicationRepository applications;
    private final ApplicationResultService results;

    public OfferReminderScheduler(ApplicationRepository applications, ApplicationResultService results) {
        this.applications = applications;
        this.results = results;
    }

    @Scheduled(fixedDelay = 60_000)
    public void sendOfferReminders() {
        LocalDateTime now = LocalDateTime.now(HONG_KONG);
        Long afterId = null;
        Slice<Application> page;
        do {
            page = applications.findOffersDueForReminder(now, now.plusHours(48), afterId, PageRequest.of(0, 100));
            for (var application : page) {
                try {
                    results.sendOfferReminder(application.getApplicationNumber());
                } catch (RuntimeException exception) {
                    log.error("Failed to send offer reminder for application {}",
                            application.getApplicationNumber(), exception);
                }
            }
            if (!page.isEmpty()) afterId = page.getContent().getLast().getId();
        } while (page.hasNext());
    }
}
