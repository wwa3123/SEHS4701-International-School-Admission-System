package hk.edu.sehs4701.cissas.service;

import hk.edu.sehs4701.cissas.entity.EmailLog;
import hk.edu.sehs4701.cissas.entity.Application;
import hk.edu.sehs4701.cissas.entity.AcademicYear;
import hk.edu.sehs4701.cissas.enums.AcademicYearStatus;
import hk.edu.sehs4701.cissas.enums.ApplicationStatus;
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
import java.time.ZoneId;

import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

class EmailSendAttemptServiceTest {
    @Test
    void sendsReminderWhileTheOfferIsStillUnansweredAndUnchanged() {
        EmailLogRepository emails = mock(EmailLogRepository.class);
        JavaMailSender sender = mock(JavaMailSender.class);
        LocalDateTime deadline = LocalDateTime.now(ZoneId.of("Asia/Hong_Kong")).plusHours(24);
        Application application = new Application();
        application.setStatus(ApplicationStatus.Success);
        application.setAcceptanceDeadline(deadline);
        AcademicYear year = new AcademicYear();
        year.setStatus(AcademicYearStatus.ACTIVE);
        application.setAcademicYear(year);
        EmailLog reminder = new EmailLog();
        reminder.setTemplateType(EmailTemplateType.OFFER_48_HOUR_REMINDER);
        reminder.setStatus(EmailDeliveryStatus.PENDING);
        reminder.setOfferDeadlineSnapshot(deadline);
        reminder.setRecipientEmail("member@example.test");
        reminder.setSubject("Offer reminder");
        reminder.setBody("Please respond in SAS");
        when(emails.findLockedById(1L)).thenReturn(Optional.of(reminder));
        when(emails.findLockedApplicationForEmailLog(1L)).thenReturn(Optional.of(application));
        var service = new EmailSendAttemptService(emails, sender, mock(ApplicationEventPublisher.class));
        ReflectionTestUtils.setField(service, "fromAddress", "school@example.test");

        service.attemptSmtpSend(new EmailSendRequestedEvent(1L, null));

        assertEquals(EmailDeliveryStatus.SENT, reminder.getStatus());
        verify(sender).send(any(org.springframework.mail.SimpleMailMessage.class));
    }

    @Test
    void skipsReminderWhenOfferDeadlineWasExtended() {
        EmailLogRepository emails = mock(EmailLogRepository.class);
        JavaMailSender sender = mock(JavaMailSender.class);
        LocalDateTime originalDeadline = LocalDateTime.now(ZoneId.of("Asia/Hong_Kong")).plusHours(24);
        Application application = new Application();
        application.setStatus(ApplicationStatus.Success);
        application.setAcceptanceDeadline(originalDeadline.plusHours(12));
        AcademicYear year = new AcademicYear();
        year.setStatus(AcademicYearStatus.ACTIVE);
        application.setAcademicYear(year);
        EmailLog reminder = new EmailLog();
        reminder.setTemplateType(EmailTemplateType.OFFER_48_HOUR_REMINDER);
        reminder.setStatus(EmailDeliveryStatus.FAILED);
        reminder.setOfferDeadlineSnapshot(originalDeadline);
        when(emails.findLockedById(1L)).thenReturn(Optional.of(reminder));
        when(emails.findLockedApplicationForEmailLog(1L)).thenReturn(Optional.of(application));

        new EmailSendAttemptService(emails, sender, mock(ApplicationEventPublisher.class))
                .attemptSmtpSend(new EmailSendRequestedEvent(1L, null));

        assertEquals(EmailDeliveryStatus.SKIPPED, reminder.getStatus());
        verifyNoInteractions(sender);
    }

    @Test
    void skipsReminderWhenMemberHasAlreadyResponded() {
        EmailLogRepository emails = mock(EmailLogRepository.class);
        JavaMailSender sender = mock(JavaMailSender.class);
        LocalDateTime deadline = LocalDateTime.now(ZoneId.of("Asia/Hong_Kong")).plusHours(24);
        Application application = new Application();
        application.setStatus(ApplicationStatus.Accepted);
        application.setRespondedAt(LocalDateTime.now(ZoneId.of("Asia/Hong_Kong")));
        application.setAcceptanceDeadline(deadline);
        AcademicYear year = new AcademicYear();
        year.setStatus(AcademicYearStatus.ACTIVE);
        application.setAcademicYear(year);
        EmailLog reminder = new EmailLog();
        reminder.setTemplateType(EmailTemplateType.OFFER_48_HOUR_REMINDER);
        reminder.setStatus(EmailDeliveryStatus.FAILED);
        reminder.setOfferDeadlineSnapshot(deadline);
        when(emails.findLockedById(1L)).thenReturn(Optional.of(reminder));
        when(emails.findLockedApplicationForEmailLog(1L)).thenReturn(Optional.of(application));

        new EmailSendAttemptService(emails, sender, mock(ApplicationEventPublisher.class))
                .attemptSmtpSend(new EmailSendRequestedEvent(1L, null));

        assertEquals(EmailDeliveryStatus.SKIPPED, reminder.getStatus());
        verifyNoInteractions(sender);
    }

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
        stubApplicationLock(emails);
        EmailLog skipped = new EmailLog();
        skipped.setStatus(EmailDeliveryStatus.SKIPPED);
        skipped.setTemplateType(EmailTemplateType.RESULT_ANNOUNCEMENT);
        when(emails.findApplicationId(1L)).thenReturn(Optional.of(7L));
        when(emails.findTemplateType(1L)).thenReturn(Optional.of(EmailTemplateType.RESULT_ANNOUNCEMENT));
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
        stubApplicationLock(emails);
        EmailLog older = new EmailLog();
        older.setId(1L);
        older.setTemplateType(EmailTemplateType.RESULT_ANNOUNCEMENT);
        EmailLog newer = new EmailLog();
        newer.setId(2L);
        newer.setTemplateType(EmailTemplateType.CORRECTED_RESULT_ANNOUNCEMENT);
        when(emails.findApplicationId(2L)).thenReturn(Optional.of(7L));
        when(emails.findTemplateType(2L)).thenReturn(Optional.of(EmailTemplateType.CORRECTED_RESULT_ANNOUNCEMENT));
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
    void acknowledgementDoesNotWaitForAnUnsentResultEmail() {
        EmailLogRepository emails = mock(EmailLogRepository.class);
        JavaMailSender sender = mock(JavaMailSender.class);
        EmailLog result = new EmailLog();
        result.setId(1L);
        result.setStatus(EmailDeliveryStatus.FAILED);
        result.setTemplateType(EmailTemplateType.RESULT_ANNOUNCEMENT);
        EmailLog acknowledgement = new EmailLog();
        acknowledgement.setId(2L);
        acknowledgement.setStatus(EmailDeliveryStatus.PENDING);
        acknowledgement.setTemplateType(EmailTemplateType.APPLICATION_ACKNOWLEDGEMENT);
        acknowledgement.setRecipientEmail("member@example.test");
        acknowledgement.setSubject("Application received");
        acknowledgement.setBody("Acknowledgement");
        when(emails.findApplicationId(2L)).thenReturn(Optional.of(7L));
        when(emails.findTemplateType(2L)).thenReturn(Optional.of(EmailTemplateType.APPLICATION_ACKNOWLEDGEMENT));
        when(emails.findLockedById(2L)).thenReturn(Optional.of(acknowledgement));
        var service = new EmailSendAttemptService(emails, sender, mock(ApplicationEventPublisher.class));
        ReflectionTestUtils.setField(service, "fromAddress", "school@example.test");

        service.attemptSmtpSend(new EmailSendRequestedEvent(2L, null));

        assertEquals(EmailDeliveryStatus.SENT, acknowledgement.getStatus());
        verify(sender).send(any(org.springframework.mail.SimpleMailMessage.class));
        verify(emails, never()).findLockedById(1L);
        verify(emails, never()).findFirstApplicationEmailId(anyLong(), anyCollection());
        verify(emails, never()).findFirstByApplication_IdAndTemplateTypeInAndStatusInOrderByIdAsc(
                anyLong(), anyCollection(), anyCollection());
    }

    @Test
    void failedAcknowledgementDoesNotBlockLaterResultEmail() {
        EmailLogRepository emails = mock(EmailLogRepository.class);
        JavaMailSender sender = mock(JavaMailSender.class);
        stubApplicationLock(emails);
        EmailLog result = new EmailLog();
        result.setId(2L);
        result.setStatus(EmailDeliveryStatus.PENDING);
        result.setTemplateType(EmailTemplateType.RESULT_ANNOUNCEMENT);
        result.setRecipientEmail("member@example.test");
        result.setSubject("Admission result");
        result.setBody("Success");
        assertFalse(EmailSendAttemptService.APPLICATION_RESULTS.contains(
                EmailTemplateType.APPLICATION_ACKNOWLEDGEMENT));
        when(emails.findApplicationId(2L)).thenReturn(Optional.of(7L));
        when(emails.findTemplateType(2L)).thenReturn(Optional.of(EmailTemplateType.RESULT_ANNOUNCEMENT));
        when(emails.findFirstApplicationEmailId(7L, EmailSendAttemptService.APPLICATION_RESULTS))
                .thenReturn(Optional.of(2L));
        when(emails.findLockedById(2L)).thenReturn(Optional.of(result));
        when(emails.findFirstByApplication_IdAndTemplateTypeInAndStatusInOrderByIdAsc(
                7L, EmailSendAttemptService.APPLICATION_RESULTS, EmailSendAttemptService.UNSENT))
                .thenReturn(Optional.of(result), Optional.empty());
        var service = new EmailSendAttemptService(emails, sender, mock(ApplicationEventPublisher.class));
        ReflectionTestUtils.setField(service, "fromAddress", "school@example.test");

        service.attemptSmtpSend(new EmailSendRequestedEvent(2L, null));

        assertEquals(EmailDeliveryStatus.SENT, result.getStatus());
        verify(sender).send(any(org.springframework.mail.SimpleMailMessage.class));
    }

    @Test
    void sendsEarlierResultAndSchedulesNextAfterCommit() {
        EmailLogRepository emails = mock(EmailLogRepository.class);
        JavaMailSender sender = mock(JavaMailSender.class);
        ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
        stubApplicationLock(emails);
        EmailLog older = new EmailLog();
        older.setId(1L);
        older.setTemplateType(EmailTemplateType.RESULT_ANNOUNCEMENT);
        older.setRecipientEmail("member@example.test");
        older.setSubject("Result");
        older.setBody("Result");
        EmailLog newer = new EmailLog();
        newer.setId(2L);
        when(emails.findApplicationId(1L)).thenReturn(Optional.of(7L));
        when(emails.findTemplateType(1L)).thenReturn(Optional.of(EmailTemplateType.RESULT_ANNOUNCEMENT));
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
        stubApplicationLock(emails);
        EmailLog pending = new EmailLog();
        pending.setId(1L);
        pending.setTemplateType(EmailTemplateType.RESULT_ANNOUNCEMENT);
        pending.setRecipientEmail("member@example.test");
        pending.setSubject("Result");
        pending.setBody("Result");
        when(emails.findApplicationId(1L)).thenReturn(Optional.of(7L));
        when(emails.findTemplateType(1L)).thenReturn(Optional.of(EmailTemplateType.RESULT_ANNOUNCEMENT));
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
        when(emails.findTemplateType(2L)).thenReturn(Optional.of(EmailTemplateType.OFFER_DEADLINE_EXTENSION));
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
        stubApplicationLock(emails);
        EmailLog failed = new EmailLog();
        failed.setId(1L);
        failed.setStatus(EmailDeliveryStatus.FAILED);
        EmailLog later = new EmailLog();
        later.setId(2L);
        later.setTemplateType(EmailTemplateType.CORRECTED_RESULT_ANNOUNCEMENT);
        when(emails.findApplicationId(2L)).thenReturn(Optional.of(7L));
        when(emails.findTemplateType(2L)).thenReturn(Optional.of(EmailTemplateType.CORRECTED_RESULT_ANNOUNCEMENT));
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
        stubApplicationLock(emails);
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
        when(emails.findTemplateType(1L)).thenReturn(Optional.of(EmailTemplateType.RESULT_ANNOUNCEMENT));
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
        when(emails.findTemplateType(2L)).thenReturn(Optional.of(EmailTemplateType.APPLICATION_DUPLICATE_ALERT));
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

    private void stubApplicationLock(EmailLogRepository emails) {
        when(emails.findLockedApplicationForEmailLog(anyLong())).thenReturn(Optional.of(new Application()));
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

    @Test
    void expiredPasswordResetEventIsNotSent() {
        EmailLogRepository emails = mock(EmailLogRepository.class);
        JavaMailSender sender = mock(JavaMailSender.class);
        EmailLog reset = new EmailLog();
        reset.setTemplateType(EmailTemplateType.PASSWORD_RESET_CODE);
        reset.setCreatedAt(LocalDateTime.now().minusMinutes(16));
        when(emails.findLockedById(2L)).thenReturn(Optional.of(reset));
        var service = new EmailSendAttemptService(emails, sender, mock(ApplicationEventPublisher.class));

        service.attemptSmtpSend(new EmailSendRequestedEvent(2L, "code"));

        assertEquals(EmailDeliveryStatus.FAILED, reset.getStatus());
        verifyNoInteractions(sender);
    }
}
