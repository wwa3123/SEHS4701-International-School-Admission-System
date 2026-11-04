package hk.edu.sehs4701.cissas.service;

import hk.edu.sehs4701.cissas.enums.EmailDeliveryStatus;
import hk.edu.sehs4701.cissas.enums.EmailTemplateType;
import hk.edu.sehs4701.cissas.repository.EmailLogRepository;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.*;

class PendingOtcEmailRecoveryServiceTest {
    @Test
    void redactsInterruptedMailAndMarksItFailedWithoutSendingOnStartup() {
        EmailLogRepository repository = mock(EmailLogRepository.class);
        PendingOtcEmailRecoveryService service = new PendingOtcEmailRecoveryService(repository);

        service.recoverInterruptedEmails();

        verify(repository).redactBodiesByTemplateType(
                EmailTemplateType.OTC_VERIFICATION,
                "[Verification code email body redacted]"
        );
        verify(repository).failPendingEmailsByTemplateType(
                EmailTemplateType.OTC_VERIFICATION,
                EmailDeliveryStatus.PENDING,
                EmailDeliveryStatus.FAILED,
                "Delivery interrupted by application shutdown; request a new verification code if needed."
        );
    }
}
