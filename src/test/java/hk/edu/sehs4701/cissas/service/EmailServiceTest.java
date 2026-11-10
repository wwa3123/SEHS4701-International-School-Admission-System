package hk.edu.sehs4701.cissas.service;

import hk.edu.sehs4701.cissas.dto.request.EmailDispatchRequest;
import hk.edu.sehs4701.cissas.entity.EmailLog;
import hk.edu.sehs4701.cissas.entity.Member;
import hk.edu.sehs4701.cissas.enums.EmailTemplateType;
import hk.edu.sehs4701.cissas.enums.EmailDeliveryStatus;
import hk.edu.sehs4701.cissas.event.EmailSendRequestedEvent;
import hk.edu.sehs4701.cissas.repository.EmailLogRepository;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.SliceImpl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class EmailServiceTest {
    @Test
    void storesRedactedBodyAndPublishesCodeOnlyForDelivery() {
        EmailLogRepository repository = mock(EmailLogRepository.class);
        ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);
        when(repository.save(any(EmailLog.class))).thenAnswer(invocation -> {
            EmailLog emailLog = invocation.getArgument(0);
            emailLog.setId(7L);
            return emailLog;
        });
        EmailService service = new EmailService(repository, eventPublisher);

        Member member = new Member();
        member.setEmail("member@example.com");
        String rawBody = "Your verification code is 123456";
        service.sendAndLogEmail(new EmailDispatchRequest(
                member, null, EmailTemplateType.OTC_VERIFICATION, "N/A", "Membership",
                "Verify your email", "Verification code", rawBody
        ));

        verify(repository).save(argThat(log ->
                log.getBody().equals("[Verification code email body redacted]")
        ));
        verify(eventPublisher).publishEvent(new EmailSendRequestedEvent(7L, rawBody));
    }

    @Test
    void refusesToResendAResultWhenANewerResultWasDelivered() {
        EmailLogRepository repository = mock(EmailLogRepository.class);
        ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
        EmailLog old = new EmailLog();
        old.setId(1L);
        old.setStatus(EmailDeliveryStatus.FAILED);
        old.setTemplateType(EmailTemplateType.RESULT_ANNOUNCEMENT);
        when(repository.findApplicationId(1L)).thenReturn(java.util.Optional.of(7L));
        when(repository.findFirstApplicationEmailId(eq(7L), anyCollection())).thenReturn(java.util.Optional.of(1L));
        when(repository.findLockedById(1L)).thenReturn(java.util.Optional.of(old));
        when(repository.findById(1L)).thenReturn(java.util.Optional.of(old));
        when(repository.existsByApplication_IdAndIdGreaterThanAndTemplateTypeInAndStatus(
                eq(7L), eq(1L), anyCollection(), eq(EmailDeliveryStatus.SENT))).thenReturn(true);

        assertThrows(IllegalStateException.class, () -> new EmailService(repository, events).manualResend(1L));
        verifyNoInteractions(events);
    }

    @Test
    void schedulesPendingAndFailedApplicationEmailHeadsInBoundedPages() {
        EmailLogRepository repository = mock(EmailLogRepository.class);
        ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
        when(repository.findUnsentApplicationEmailHeads(
                eq(java.util.List.of(EmailDeliveryStatus.PENDING, EmailDeliveryStatus.FAILED)),
                eq(EmailSendAttemptService.APPLICATION_RESULTS), eq(0L), eq(PageRequest.of(0, 100))))
                .thenReturn(new SliceImpl<>(java.util.List.of(14L, 15L), PageRequest.of(0, 100), true));

        assertEquals(15L, new EmailService(repository, events).retryUnsentApplicationEmails(0L));
        verify(events).publishEvent(new EmailSendRequestedEvent(14L, null));
        verify(events).publishEvent(new EmailSendRequestedEvent(15L, null));
    }

    @Test
    void skippingFailedHeadRecordsAdministratorAndWakesLaterEmail() {
        EmailLogRepository repository = mock(EmailLogRepository.class);
        ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
        EmailLog failed = new EmailLog();
        failed.setId(1L);
        failed.setTemplateType(EmailTemplateType.RESULT_ANNOUNCEMENT);
        failed.setStatus(EmailDeliveryStatus.FAILED);
        EmailLog next = new EmailLog();
        next.setId(2L);
        when(repository.findApplicationId(1L)).thenReturn(java.util.Optional.of(7L));
        when(repository.findFirstApplicationEmailId(7L, EmailSendAttemptService.APPLICATION_RESULTS))
                .thenReturn(java.util.Optional.of(1L));
        when(repository.findLockedById(1L)).thenReturn(java.util.Optional.of(failed));
        when(repository.findFirstByApplication_IdAndTemplateTypeInAndStatusInOrderByIdAsc(
                7L, EmailSendAttemptService.APPLICATION_RESULTS, EmailSendAttemptService.UNSENT))
                .thenReturn(java.util.Optional.of(failed), java.util.Optional.of(next));

        new EmailService(repository, events).skipFailedApplicationEmail(1L, 42L, "  Rejected by mail server  ");

        assertEquals(EmailDeliveryStatus.SKIPPED, failed.getStatus());
        assertEquals(42L, failed.getSkippedByAdminId());
        assertNotNull(failed.getSkippedAt());
        assertEquals("Rejected by mail server", failed.getSkipReason());
        verify(events).publishEvent(new EmailSendRequestedEvent(2L, null));
    }

    @Test
    void refusesToSkipAnEmailThatIsNotTheFailedQueueHead() {
        EmailLogRepository repository = mock(EmailLogRepository.class);
        ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
        EmailLog first = new EmailLog();
        first.setId(1L);
        EmailLog failed = new EmailLog();
        failed.setId(2L);
        failed.setStatus(EmailDeliveryStatus.FAILED);
        failed.setTemplateType(EmailTemplateType.RESULT_ANNOUNCEMENT);
        when(repository.findApplicationId(2L)).thenReturn(java.util.Optional.of(7L));
        when(repository.findFirstApplicationEmailId(7L, EmailSendAttemptService.APPLICATION_RESULTS))
                .thenReturn(java.util.Optional.of(1L));
        when(repository.findLockedById(1L)).thenReturn(java.util.Optional.of(first));
        when(repository.findLockedById(2L)).thenReturn(java.util.Optional.of(failed));
        when(repository.findFirstByApplication_IdAndTemplateTypeInAndStatusInOrderByIdAsc(
                7L, EmailSendAttemptService.APPLICATION_RESULTS, EmailSendAttemptService.UNSENT))
                .thenReturn(java.util.Optional.of(first));

        assertThrows(IllegalStateException.class,
                () -> new EmailService(repository, events).skipFailedApplicationEmail(2L, 42L, "Undeliverable"));
        assertEquals(EmailDeliveryStatus.FAILED, failed.getStatus());
        verifyNoInteractions(events);
    }

    @Test
    void refusesToResendSkippedEmail() {
        EmailLogRepository repository = mock(EmailLogRepository.class);
        EmailLog skipped = new EmailLog();
        skipped.setId(1L);
        skipped.setStatus(EmailDeliveryStatus.SKIPPED);
        skipped.setTemplateType(EmailTemplateType.RESULT_ANNOUNCEMENT);
        when(repository.findApplicationId(1L)).thenReturn(java.util.Optional.of(7L));
        when(repository.findLockedById(1L)).thenReturn(java.util.Optional.of(skipped));
        assertThrows(IllegalStateException.class,
                () -> new EmailService(repository, mock(ApplicationEventPublisher.class)).manualResend(1L));
    }

    @Test
    void schedulesDuplicateAlertsSeparatelyFromResultQueue() {
        EmailLogRepository repository = mock(EmailLogRepository.class);
        ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
        EmailLog alert = new EmailLog();
        alert.setId(8L);
        when(repository.findAllByStatusInAndTemplateTypeAndIdGreaterThanOrderByIdAsc(
                EmailSendAttemptService.UNSENT, EmailTemplateType.APPLICATION_DUPLICATE_ALERT, 0L,
                PageRequest.of(0, 100)))
                .thenReturn(new SliceImpl<>(java.util.List.of(alert), PageRequest.of(0, 100), true));

        assertEquals(8L, new EmailService(repository, events).retryUnsentDuplicateAlerts(0L));
        verify(events).publishEvent(new EmailSendRequestedEvent(8L, null));
    }

    @Test
    void refusesToResendDeliveredAlert() {
        EmailLogRepository repository = mock(EmailLogRepository.class);
        EmailLog sent = new EmailLog();
        sent.setTemplateType(EmailTemplateType.APPLICATION_DUPLICATE_ALERT);
        sent.setStatus(EmailDeliveryStatus.SENT);
        when(repository.findLockedById(8L)).thenReturn(java.util.Optional.of(sent));
        assertThrows(IllegalStateException.class,
                () -> new EmailService(repository, mock(ApplicationEventPublisher.class)).manualResend(8L));
    }
}
