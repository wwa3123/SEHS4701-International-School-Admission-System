package hk.edu.sehs4701.cissas.service;

import hk.edu.sehs4701.cissas.enums.EmailDeliveryStatus;
import hk.edu.sehs4701.cissas.enums.EmailTemplateType;
import hk.edu.sehs4701.cissas.repository.EmailLogRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PendingOtcEmailRecoveryService {
    private static final String REDACTED_BODY = "[Verification code email body redacted]";

    private final EmailLogRepository emailLogRepository;

    @Autowired
    public PendingOtcEmailRecoveryService(EmailLogRepository emailLogRepository) {
        this.emailLogRepository = emailLogRepository;
    }

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void recoverInterruptedEmails() {
        emailLogRepository.redactBodiesByTemplateType(EmailTemplateType.OTC_VERIFICATION, REDACTED_BODY);
        emailLogRepository.failPendingEmailsByTemplateType(
                EmailTemplateType.OTC_VERIFICATION,
                EmailDeliveryStatus.PENDING,
                EmailDeliveryStatus.FAILED,
                "Delivery interrupted by application shutdown; request a new verification code if needed."
        );
    }
}
