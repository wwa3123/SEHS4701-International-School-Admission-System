package hk.edu.sehs4701.cissas.service;

import hk.edu.sehs4701.cissas.enums.EmailDeliveryStatus;
import hk.edu.sehs4701.cissas.enums.EmailTemplateType;
import hk.edu.sehs4701.cissas.repository.EmailLogRepository;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.event.ApplicationReadyEvent;

import static org.mockito.Mockito.*;

class PendingOtcEmailRecoveryServiceTest {
    @Test
    void redactsInterruptedMailAndMarksItFailedWithoutSendingOnStartup() {
        EmailLogRepository repository = mock(EmailLogRepository.class);
        PendingOtcEmailRecoveryService service = new PendingOtcEmailRecoveryService(repository);

        ApplicationReadyEvent event = mock(ApplicationReadyEvent.class);
        when(event.getArgs()).thenReturn(new String[0]);
        service.recoverInterruptedEmails(event);

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

    @Test
    void superAdminRecoveryDoesNotChangeEmailRecords() {
        EmailLogRepository repository = mock(EmailLogRepository.class);
        PendingOtcEmailRecoveryService service = new PendingOtcEmailRecoveryService(repository);
        ApplicationReadyEvent event = mock(ApplicationReadyEvent.class);
        when(event.getArgs()).thenReturn(new String[]{"--recover-super-admin"});

        service.recoverInterruptedEmails(event);

        verifyNoInteractions(repository);
    }
}
