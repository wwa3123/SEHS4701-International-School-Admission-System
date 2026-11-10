package hk.edu.sehs4701.cissas.service;

import hk.edu.sehs4701.cissas.dto.request.EmailDispatchRequest;
import hk.edu.sehs4701.cissas.entity.Application;
import hk.edu.sehs4701.cissas.entity.ApplicantSnapshot;
import hk.edu.sehs4701.cissas.entity.Member;
import hk.edu.sehs4701.cissas.entity.ProgrammeLevel;
import hk.edu.sehs4701.cissas.enums.EmailTemplateType;
import hk.edu.sehs4701.cissas.enums.IdentityDocumentType;
import hk.edu.sehs4701.cissas.enums.LevelCode;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

class NotificationServiceTest {
    @Test
    void buildsVerificationEmailAndDispatchesIt() {
        EmailService emailService = mock(EmailService.class);
        NotificationService service = new NotificationService(emailService);
        Member member = new Member();

        service.sendVerificationCode(member, "123456", 15);

        ArgumentCaptor<EmailDispatchRequest> requestCaptor = ArgumentCaptor.forClass(EmailDispatchRequest.class);
        verify(emailService).sendAndLogEmail(requestCaptor.capture());
        EmailDispatchRequest request = requestCaptor.getValue();
        assertEquals(member, request.member());
        assertEquals(EmailTemplateType.OTC_VERIFICATION, request.templateType());
        assertEquals("[CIS SAS] Email Verification Code", request.subject());
        assertTrue(request.body().contains("Dear New CIS Member"));
        assertTrue(request.body().contains("code is: 123456"));
        assertTrue(request.body().contains("expires in 15 minutes"));
    }

    @Test
    void sendsIdentityReviewAlertToConfiguredAdminAddress() {
        EmailService emailService = mock(EmailService.class);
        NotificationService service = new NotificationService(emailService);
        ReflectionTestUtils.setField(service, "adminNotificationEmail", " Admissions@Example.com ");

        Member member = new Member();
        member.setEmail("member@example.com");
        ProgrammeLevel level = new ProgrammeLevel();
        level.setLevelCode(LevelCode.S1);
        Application application = new Application();
        application.setApplicationNumber("APP-123");
        application.setProgrammeLevel(level);
        application.setApplicantSnapshot(new ApplicantSnapshot(
                "Applicant Name", LocalDate.of(2018, 1, 1), "Unspecified",
                "12345678", "Previous School", IdentityDocumentType.HKID, "A123456(3)", null
        ));

        service.notifyAdminOfPossibleDuplicate(member, application, "2026/27");

        ArgumentCaptor<EmailDispatchRequest> requestCaptor = ArgumentCaptor.forClass(EmailDispatchRequest.class);
        verify(emailService).sendAndLogEmail(requestCaptor.capture());
        EmailDispatchRequest request = requestCaptor.getValue();
        assertEquals(EmailTemplateType.APPLICATION_DUPLICATE_ALERT, request.templateType());
        assertEquals("admissions@example.com", request.recipientEmail());
        assertEquals(application, request.application());
        assertTrue(request.body().contains("Applicant Name"));
        assertTrue(request.body().contains("2018-01-01"));
    }

    @Test
    void logsMissingAdminAddressInsteadOfBlockingSubmissionNotificationFlow() {
        EmailService emailService = mock(EmailService.class);
        NotificationService service = new NotificationService(emailService);
        ReflectionTestUtils.setField(service, "adminNotificationEmail", "");

        Application application = new Application();
        application.setApplicationNumber("APP-123");

        service.notifyAdminOfPossibleDuplicate(new Member(), application, "2026/27");

        verifyNoInteractions(emailService);
    }

    @Test
    void sendsOfferWithAcceptanceDeadlineAndCorrectedResultReason() {
        EmailService emailService = mock(EmailService.class);
        NotificationService service = new NotificationService(emailService);
        Member member = new Member();
        member.setFirstName("Member");
        Application application = new Application();
        application.setMember(member);
        application.setApplicationNumber("APP-456");
        application.setStatus(hk.edu.sehs4701.cissas.enums.ApplicationStatus.Success);
        application.setAcceptanceDeadline(LocalDateTime.of(2026, 11, 16, 12, 0));
        application.setApplicantSnapshot(new ApplicantSnapshot(
                "Applicant Name", LocalDate.of(2018, 1, 1), "Unspecified",
                "12345678", "Previous School", IdentityDocumentType.HKID, "A123456(3)", null
        ));
        ProgrammeLevel level = new ProgrammeLevel();
        level.setProgrammeName("Primary");
        application.setProgrammeLevel(level);

        service.notifyApplicationResult(application, true, "Corrected clerical error", false);

        ArgumentCaptor<EmailDispatchRequest> requestCaptor = ArgumentCaptor.forClass(EmailDispatchRequest.class);
        verify(emailService).sendAndLogEmail(requestCaptor.capture());
        EmailDispatchRequest request = requestCaptor.getValue();
        assertEquals(EmailTemplateType.CORRECTED_RESULT_ANNOUNCEMENT, request.templateType());
        assertTrue(request.body().contains("Result released: Success"));
        assertTrue(request.body().contains("Success means an offer. If it is still awaiting your response, "
                + "accept or reject it in SAS before the current deadline."));
        assertTrue(request.body().contains("Log in to SAS to check your current application status"));
        assertTrue(request.body().contains("The deadline or result may have changed"));
        assertTrue(request.body().contains("2026-11-16T12:00"));
        assertTrue(request.body().contains("Corrected clerical error"));

        clearInvocations(emailService);
        application.setStatus(hk.edu.sehs4701.cissas.enums.ApplicationStatus.Wait);
        application.setAcceptanceDeadline(null);
        service.notifyApplicationResult(application, false, null, false);
        verify(emailService).sendAndLogEmail(requestCaptor.capture());
        assertFalse(requestCaptor.getValue().body().contains("accept or reject"));
    }

    @Test
    void deadlineExtensionEmailIdentifiesOfferAndReplacesEarlierDeadline() {
        EmailService emailService = mock(EmailService.class);
        NotificationService service = new NotificationService(emailService);
        Member member = new Member();
        member.setFirstName("Member");
        member.setEmail("member@example.com");
        Application application = new Application();
        application.setMember(member);
        application.setApplicationNumber("APP-456");
        application.setAcceptanceDeadline(LocalDateTime.of(2026, 11, 18, 12, 0));
        application.setApplicantSnapshot(new ApplicantSnapshot(
                "Applicant Name", LocalDate.of(2018, 1, 1), "Unspecified",
                "12345678", "Previous School", IdentityDocumentType.HKID, "A123456(3)", null));
        ProgrammeLevel level = new ProgrammeLevel();
        level.setProgrammeName("Primary");
        application.setProgrammeLevel(level);

        service.notifyOfferDeadlineExtended(application);

        ArgumentCaptor<EmailDispatchRequest> captured = ArgumentCaptor.forClass(EmailDispatchRequest.class);
        verify(emailService).sendAndLogEmail(captured.capture());
        EmailDispatchRequest request = captured.getValue();
        assertEquals(EmailTemplateType.OFFER_DEADLINE_EXTENSION, request.templateType());
        assertEquals(application, request.application());
        assertEquals("member@example.com", request.recipientEmail());
        assertTrue(request.body().contains("APP-456"));
        assertTrue(request.body().contains("Primary"));
        assertTrue(request.body().contains("2026-11-18T12:00"));
        assertTrue(request.body().contains("deadline for your offer was extended"));
        assertTrue(request.body().contains("Log in to SAS to check your current application status"));
        assertTrue(request.body().contains("The deadline or result may have changed"));
    }
}
