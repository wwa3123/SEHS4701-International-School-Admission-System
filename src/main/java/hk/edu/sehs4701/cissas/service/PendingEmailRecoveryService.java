package hk.edu.sehs4701.cissas.service;

import hk.edu.sehs4701.cissas.enums.EmailDeliveryStatus;
import hk.edu.sehs4701.cissas.enums.EmailTemplateType;
import hk.edu.sehs4701.cissas.event.EmailSendRequestedEvent;
import hk.edu.sehs4701.cissas.repository.EmailLogRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.scheduling.annotation.Scheduled;

import java.util.Arrays;
import java.time.LocalDateTime;

@Service
public class PendingEmailRecoveryService {
    private static final String REDACTED_BODY = "[Verification code email body redacted]";

    private final EmailLogRepository emailLogRepository;
    private final ApplicationEventPublisher events;

    @Value("${cissas.otc.expiry-minutes:15}")
    private int otcExpiryMinutes;

    @Autowired
    public PendingEmailRecoveryService(EmailLogRepository emailLogRepository, ApplicationEventPublisher events) {
        this.emailLogRepository = emailLogRepository;
        this.events = events;
    }

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void recoverInterruptedEmails(ApplicationReadyEvent event) {
        if (Arrays.asList(event.getArgs()).contains("--recover-super-admin")) {
            return;
        }
        emailLogRepository.redactBodiesByTemplateType(EmailTemplateType.OTC_VERIFICATION, REDACTED_BODY);
        failExpiredVerificationEmails();
    }

    @Scheduled(fixedDelay = 300_000)
    @Transactional
    public void failExpiredVerificationEmails() {
        emailLogRepository.failExpiredPendingEmailsByTemplateType(
                EmailTemplateType.OTC_VERIFICATION,
                EmailDeliveryStatus.PENDING,
                EmailDeliveryStatus.FAILED,
                LocalDateTime.now().minusMinutes(otcExpiryMinutes),
                "Verification code expired before delivery; request a new code."
        );
    }

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void recoverApplicationEmails(ApplicationReadyEvent event) {
        if (Arrays.asList(event.getArgs()).contains("--recover-super-admin")) return;
        emailLogRepository.findAllByStatusAndTemplateTypeIn(EmailDeliveryStatus.PENDING,
                        EmailSendAttemptService.RECOVERABLE_APPLICATION_EMAILS)
                .forEach(email -> events.publishEvent(new EmailSendRequestedEvent(email.getId(), null)));
    }
}
