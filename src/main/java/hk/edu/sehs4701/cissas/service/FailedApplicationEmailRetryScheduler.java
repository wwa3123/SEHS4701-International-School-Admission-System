package hk.edu.sehs4701.cissas.service;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class FailedApplicationEmailRetryScheduler {
    private final EmailService emails;

    public FailedApplicationEmailRetryScheduler(EmailService emails) {
        this.emails = emails;
    }

    @Scheduled(initialDelay = 300_000, fixedDelay = 300_000)
    public void retryUnsentEmails() {
        Long afterId = 0L;
        do {
            afterId = emails.retryUnsentApplicationEmails(afterId);
        } while (afterId != null);
        afterId = 0L;
        do {
            afterId = emails.retryUnsentDuplicateAlerts(afterId);
        } while (afterId != null);
    }
}
