package hk.edu.sehs4701.cissas.service;

import hk.edu.sehs4701.cissas.enums.EmailDeliveryStatus;
import hk.edu.sehs4701.cissas.enums.EmailTemplateType;
import hk.edu.sehs4701.cissas.entity.EmailLog;
import hk.edu.sehs4701.cissas.event.EmailSendRequestedEvent;
import hk.edu.sehs4701.cissas.repository.EmailLogRepository;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

class PendingEmailRecoveryServiceTest {
    @Test
    void startupOnlyMarksExpiredVerificationEmailsFailed() {
        EmailLogRepository repository = mock(EmailLogRepository.class);
        PendingEmailRecoveryService service = new PendingEmailRecoveryService(repository, mock(ApplicationEventPublisher.class));
        ReflectionTestUtils.setField(service, "otcExpiryMinutes", 15);
        LocalDateTime before = LocalDateTime.now().minusMinutes(15);

        ApplicationReadyEvent event = mock(ApplicationReadyEvent.class);
        when(event.getArgs()).thenReturn(new String[0]);
        service.recoverInterruptedEmails(event);

        verify(repository).redactBodiesByTemplateType(
                EmailTemplateType.OTC_VERIFICATION,
                "[Verification code email body redacted]"
        );
        verify(repository).failExpiredPendingEmailsByTemplateType(
                eq(EmailTemplateType.OTC_VERIFICATION), eq(EmailDeliveryStatus.PENDING),
                eq(EmailDeliveryStatus.FAILED),
                argThat(cutoff -> !cutoff.isBefore(before) && !cutoff.isAfter(LocalDateTime.now().minusMinutes(15))),
                eq("Verification code expired before delivery; request a new code."));
    }

    @Test
    void superAdminRecoveryDoesNotChangeEmailRecords() {
        EmailLogRepository repository = mock(EmailLogRepository.class);
        ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
        PendingEmailRecoveryService service = new PendingEmailRecoveryService(repository, events);
        ApplicationReadyEvent event = mock(ApplicationReadyEvent.class);
        when(event.getArgs()).thenReturn(new String[]{"--recover-super-admin"});

        service.recoverInterruptedEmails(event);
        service.recoverApplicationEmails(event);

        verifyNoInteractions(repository, events);
    }

    @Test
    void republishesPendingApplicationEmailsAtStartup() {
        EmailLogRepository emails = mock(EmailLogRepository.class);
        ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
        ApplicationReadyEvent ready = mock(ApplicationReadyEvent.class);
        when(ready.getArgs()).thenReturn(new String[0]);
        EmailLog pending = new EmailLog();
        pending.setId(12L);
        when(emails.findAllByStatusAndTemplateTypeIn(eq(EmailDeliveryStatus.PENDING), anyCollection()))
                .thenReturn(List.of(pending));

        new PendingEmailRecoveryService(emails, events).recoverApplicationEmails(ready);

        verify(emails).findAllByStatusAndTemplateTypeIn(EmailDeliveryStatus.PENDING,
                EmailSendAttemptService.RECOVERABLE_APPLICATION_EMAILS);
        assertTrue(EmailSendAttemptService.APPLICATION_RESULTS.contains(EmailTemplateType.OFFER_DEADLINE_EXTENSION));
        assertTrue(EmailSendAttemptService.RECOVERABLE_APPLICATION_EMAILS.contains(EmailTemplateType.APPLICATION_DUPLICATE_ALERT));
        verify(events).publishEvent(new EmailSendRequestedEvent(12L, null));
    }
}
