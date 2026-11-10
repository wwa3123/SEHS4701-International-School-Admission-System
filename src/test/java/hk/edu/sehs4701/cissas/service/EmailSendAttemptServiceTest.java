package hk.edu.sehs4701.cissas.service;

import hk.edu.sehs4701.cissas.entity.EmailLog;
import hk.edu.sehs4701.cissas.enums.EmailDeliveryStatus;
import hk.edu.sehs4701.cissas.enums.EmailTemplateType;
import hk.edu.sehs4701.cissas.event.EmailSendRequestedEvent;
import hk.edu.sehs4701.cissas.repository.EmailLogRepository;
import org.junit.jupiter.api.Test;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;
import java.time.LocalDateTime;

import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

class EmailSendAttemptServiceTest {
    @Test
    void ignoresRecoveredEmailAlreadySentByAnotherWorker() {
        EmailLogRepository emails = mock(EmailLogRepository.class);
        JavaMailSender sender = mock(JavaMailSender.class);
        EmailLog sent = new EmailLog();
        sent.setStatus(EmailDeliveryStatus.SENT);
        when(emails.findLockedById(1L)).thenReturn(Optional.of(sent));

        new EmailSendAttemptService(emails, sender, mock(ApplicationEventPublisher.class))
                .attemptSmtpSend(new EmailSendRequestedEvent(1L, null));

        verifyNoInteractions(sender);
    }

    @Test
    void ignoresStaleSendEventForSkippedEmail() {
        EmailLogRepository emails = mock(EmailLogRepository.class);
        JavaMailSender sender = mock(JavaMailSender.class);
        EmailLog skipped = new EmailLog();
        skipped.setStatus(EmailDeliveryStatus.SKIPPED);
        skipped.setTemplateType(EmailTemplateType.RESULT_ANNOUNCEMENT);
        when(emails.findApplicationId(1L)).thenReturn(Optional.of(7L));
        when(emails.findFirstApplicationEmailId(eq(7L), anyCollection())).thenReturn(Optional.of(1L));
        when(emails.findLockedById(1L)).thenReturn(Optional.of(skipped));

        new EmailSendAttemptService(emails, sender, mock(ApplicationEventPublisher.class))
                .attemptSmtpSend(new EmailSendRequestedEvent(1L, null));
        verifyNoInteractions(sender);
    }

    @Test
    void marksFailedOnlyIfEmailIsStillPendingAfterRetries() {
        EmailLogRepository emails = mock(EmailLogRepository.class);
        EmailSendAttemptService service = new EmailSendAttemptService(
                emails, mock(JavaMailSender.class), mock(ApplicationEventPublisher.class));

        service.recover(new MailSendException("mail unavailable"), new EmailSendRequestedEvent(1L, null));

        verify(emails).failIfPending(1L, EmailDeliveryStatus.PENDING, EmailDeliveryStatus.FAILED, "mail unavailable");
    }

    @Test
    void laterResultWaitsForEarlierResult() {
        EmailLogRepository emails = mock(EmailLogRepository.class);
        JavaMailSender sender = mock(JavaMailSender.class);
        EmailLog older = new EmailLog();
        older.setId(1L);
        older.setTemplateType(EmailTemplateType.RESULT_ANNOUNCEMENT);
        EmailLog newer = new EmailLog();
        newer.setId(2L);
        newer.setTemplateType(EmailTemplateType.CORRECTED_RESULT_ANNOUNCEMENT);
        when(emails.findApplicationId(2L)).thenReturn(Optional.of(7L));
        when(emails.findFirstApplicationEmailId(eq(7L), anyCollection())).thenReturn(Optional.of(1L));
        when(emails.findLockedById(1L)).thenReturn(Optional.of(older));
        when(emails.findLockedById(2L)).thenReturn(Optional.of(newer));
        when(emails.findFirstByApplication_IdAndTemplateTypeInAndStatusInOrderByIdAsc(
                eq(7L), anyCollection(), eq(EmailSendAttemptService.UNSENT))).thenReturn(Optional.of(older));

        new EmailSendAttemptService(emails, sender, mock(ApplicationEventPublisher.class))
                .attemptSmtpSend(new EmailSendRequestedEvent(2L, null));

        assertEquals(EmailDeliveryStatus.PENDING, older.getStatus());
        assertEquals(EmailDeliveryStatus.PENDING, newer.getStatus());
        var order = inOrder(emails);
        order.verify(emails).findLockedById(1L);
        order.verify(emails).findLockedById(2L);
        verifyNoInteractions(sender);
    }

    @Test
    void sendsEarlierResultAndSchedulesNextAfterCommit() {
        EmailLogRepository emails = mock(EmailLogRepository.class);
        JavaMailSender sender = mock(JavaMailSender.class);
        ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
        EmailLog older = new EmailLog();
        older.setId(1L);
        older.setTemplateType(EmailTemplateType.RESULT_ANNOUNCEMENT);
        older.setRecipientEmail("member@example.test");
        older.setSubject("Result");
        older.setBody("Result");
        EmailLog newer = new EmailLog();
        newer.setId(2L);
        when(emails.findApplicationId(1L)).thenReturn(Optional.of(7L));
        when(emails.findFirstApplicationEmailId(eq(7L), anyCollection())).thenReturn(Optional.of(1L));
        when(emails.findLockedById(1L)).thenReturn(Optional.of(older));
        when(emails.findFirstByApplication_IdAndTemplateTypeInAndStatusInOrderByIdAsc(
                eq(7L), anyCollection(), eq(EmailSendAttemptService.UNSENT)))
                .thenReturn(Optional.of(older), Optional.of(newer));
        var service = new EmailSendAttemptService(emails, sender, events);
        ReflectionTestUtils.setField(service, "fromAddress", "school@example.test");

        service.attemptSmtpSend(new EmailSendRequestedEvent(1L, null));

        assertEquals(EmailDeliveryStatus.SENT, older.getStatus());
        verify(events).publishEvent(new EmailSendRequestedEvent(2L, null));
        verify(sender).send(any(org.springframework.mail.SimpleMailMessage.class));
    }

    @Test
    void recoveredPendingEmailIsSentOnlyOnceWhenEventIsRepeated() {
        EmailLogRepository emails = mock(EmailLogRepository.class);
        JavaMailSender sender = mock(JavaMailSender.class);
        EmailLog pending = new EmailLog();
        pending.setId(1L);
        pending.setTemplateType(EmailTemplateType.RESULT_ANNOUNCEMENT);
        pending.setRecipientEmail("member@example.test");
        pending.setSubject("Result");
        pending.setBody("Result");
        when(emails.findApplicationId(1L)).thenReturn(Optional.of(7L));
        when(emails.findFirstApplicationEmailId(eq(7L), anyCollection())).thenReturn(Optional.of(1L));
        when(emails.findLockedById(1L)).thenReturn(Optional.of(pending));
        when(emails.findFirstByApplication_IdAndTemplateTypeInAndStatusInOrderByIdAsc(
                eq(7L), anyCollection(), eq(EmailSendAttemptService.UNSENT)))
                .thenReturn(Optional.of(pending), Optional.empty());
        var service = new EmailSendAttemptService(emails, sender, mock(ApplicationEventPublisher.class));
        ReflectionTestUtils.setField(service, "fromAddress", "school@example.test");

        service.attemptSmtpSend(new EmailSendRequestedEvent(1L, null));
        service.attemptSmtpSend(new EmailSendRequestedEvent(1L, null));

        assertEquals(EmailDeliveryStatus.SENT, pending.getStatus());
        verify(sender, times(1)).send(any(org.springframework.mail.SimpleMailMessage.class));
    }

    @Test
    void deadlineUpdateWaitsForEarlierOfferEmail() {
        EmailLogRepository emails = mock(EmailLogRepository.class);
        JavaMailSender sender = mock(JavaMailSender.class);
        EmailLog earlier = new EmailLog();
        earlier.setId(1L);
        earlier.setTemplateType(EmailTemplateType.RESULT_ANNOUNCEMENT);
        EmailLog update = new EmailLog();
        update.setId(2L);
        update.setTemplateType(EmailTemplateType.OFFER_DEADLINE_EXTENSION);
        when(emails.findApplicationId(2L)).thenReturn(Optional.of(7L));
        when(emails.findFirstApplicationEmailId(eq(7L), anyCollection())).thenReturn(Optional.of(1L));
        when(emails.findLockedById(1L)).thenReturn(Optional.of(earlier));
        when(emails.findLockedById(2L)).thenReturn(Optional.of(update));
        when(emails.findFirstByApplication_IdAndTemplateTypeInAndStatusInOrderByIdAsc(
                eq(7L), eq(EmailSendAttemptService.APPLICATION_RESULTS), eq(EmailSendAttemptService.UNSENT)))
                .thenReturn(Optional.of(earlier));

        new EmailSendAttemptService(emails, sender, mock(ApplicationEventPublisher.class))
                .attemptSmtpSend(new EmailSendRequestedEvent(2L, null));

        assertEquals(EmailDeliveryStatus.PENDING, update.getStatus());
        verifyNoInteractions(sender);
    }

    @Test
    void failedEarlierResultBlocksLaterUntilResent() {
        EmailLogRepository emails = mock(EmailLogRepository.class);
        JavaMailSender sender = mock(JavaMailSender.class);
        EmailLog failed = new EmailLog();
        failed.setId(1L);
        failed.setStatus(EmailDeliveryStatus.FAILED);
        EmailLog later = new EmailLog();
        later.setId(2L);
        later.setTemplateType(EmailTemplateType.CORRECTED_RESULT_ANNOUNCEMENT);
        when(emails.findApplicationId(2L)).thenReturn(Optional.of(7L));
        when(emails.findFirstApplicationEmailId(eq(7L), anyCollection())).thenReturn(Optional.of(1L));
        when(emails.findLockedById(1L)).thenReturn(Optional.of(failed));
        when(emails.findLockedById(2L)).thenReturn(Optional.of(later));
        when(emails.findFirstByApplication_IdAndTemplateTypeInAndStatusInOrderByIdAsc(
                eq(7L), anyCollection(), eq(EmailSendAttemptService.UNSENT))).thenReturn(Optional.of(failed));

        new EmailSendAttemptService(emails, sender, mock(ApplicationEventPublisher.class))
                .attemptSmtpSend(new EmailSendRequestedEvent(2L, null));

        assertEquals(EmailDeliveryStatus.PENDING, later.getStatus());
        verifyNoInteractions(sender);
    }

    @Test
    void scheduledRetryCanSendFailedHeadAndWakeNextEmail() {
        EmailLogRepository emails = mock(EmailLogRepository.class);
        JavaMailSender sender = mock(JavaMailSender.class);
        ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
        EmailLog failed = new EmailLog();
        failed.setId(1L);
        failed.setStatus(EmailDeliveryStatus.FAILED);
        failed.setTemplateType(EmailTemplateType.RESULT_ANNOUNCEMENT);
        failed.setRecipientEmail("member@example.test");
        failed.setSubject("Result");
        failed.setBody("Result");
        EmailLog next = new EmailLog();
        next.setId(2L);
        when(emails.findApplicationId(1L)).thenReturn(Optional.of(7L));
        when(emails.findFirstApplicationEmailId(eq(7L), anyCollection())).thenReturn(Optional.of(1L));
        when(emails.findLockedById(1L)).thenReturn(Optional.of(failed));
        when(emails.findFirstByApplication_IdAndTemplateTypeInAndStatusInOrderByIdAsc(
                eq(7L), anyCollection(), eq(EmailSendAttemptService.UNSENT)))
                .thenReturn(Optional.of(failed), Optional.of(next));
        var service = new EmailSendAttemptService(emails, sender, events);
        ReflectionTestUtils.setField(service, "fromAddress", "school@example.test");

        service.attemptSmtpSend(new EmailSendRequestedEvent(1L, null));

        assertEquals(EmailDeliveryStatus.SENT, failed.getStatus());
        verify(events).publishEvent(new EmailSendRequestedEvent(2L, null));
    }

    @Test
    void failedDuplicateAlertIsSentWithoutWaitingForResultQueue() {
        EmailLogRepository emails = mock(EmailLogRepository.class);
        JavaMailSender sender = mock(JavaMailSender.class);
        EmailLog result = new EmailLog();
        EmailLog alert = new EmailLog();
        alert.setId(2L);
        alert.setTemplateType(EmailTemplateType.APPLICATION_DUPLICATE_ALERT);
        alert.setStatus(EmailDeliveryStatus.FAILED);
        alert.setRecipientEmail("admin@example.test");
        alert.setSubject("Possible duplicate");
        alert.setBody("Please review");
        when(emails.findApplicationId(2L)).thenReturn(Optional.of(7L));
        when(emails.findFirstApplicationEmailId(7L, EmailSendAttemptService.APPLICATION_RESULTS))
                .thenReturn(Optional.of(1L));
        when(emails.findLockedById(1L)).thenReturn(Optional.of(result));
        when(emails.findLockedById(2L)).thenReturn(Optional.of(alert));
        var service = new EmailSendAttemptService(emails, sender, mock(ApplicationEventPublisher.class));
        ReflectionTestUtils.setField(service, "fromAddress", "school@example.test");

        service.attemptSmtpSend(new EmailSendRequestedEvent(2L, null));
        service.attemptSmtpSend(new EmailSendRequestedEvent(2L, null));

        assertEquals(EmailDeliveryStatus.SENT, alert.getStatus());
        verify(sender, times(1)).send(any(org.springframework.mail.SimpleMailMessage.class));
        verify(emails, never()).findFirstByApplication_IdAndTemplateTypeInAndStatusInOrderByIdAsc(
                anyLong(), anyCollection(), anyCollection());
    }

    @Test
    void expiredVerificationEventIsNotSent() {
        EmailLogRepository emails = mock(EmailLogRepository.class);
        JavaMailSender sender = mock(JavaMailSender.class);
        EmailLog verification = new EmailLog();
        verification.setTemplateType(EmailTemplateType.OTC_VERIFICATION);
        verification.setCreatedAt(LocalDateTime.now().minusMinutes(16));
        when(emails.findLockedById(1L)).thenReturn(Optional.of(verification));
        var service = new EmailSendAttemptService(emails, sender, mock(ApplicationEventPublisher.class));
        ReflectionTestUtils.setField(service, "otcExpiryMinutes", 15);

        service.attemptSmtpSend(new EmailSendRequestedEvent(1L, "code"));

        assertEquals(EmailDeliveryStatus.FAILED, verification.getStatus());
        verifyNoInteractions(sender);
    }
}
