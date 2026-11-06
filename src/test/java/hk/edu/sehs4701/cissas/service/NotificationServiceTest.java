package hk.edu.sehs4701.cissas.service;

import hk.edu.sehs4701.cissas.dto.request.EmailDispatchRequest;
import hk.edu.sehs4701.cissas.entity.Application;
import hk.edu.sehs4701.cissas.entity.Applicant;
import hk.edu.sehs4701.cissas.entity.Member;
import hk.edu.sehs4701.cissas.entity.ProgrammeLevel;
import hk.edu.sehs4701.cissas.enums.EmailTemplateType;
import hk.edu.sehs4701.cissas.enums.LevelCode;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

class NotificationServiceTest {
    @Test
    void buildsVerificationEmailAndDispatchesIt() {
        EmailService emailService = mock(EmailService.class);
        NotificationService service = new NotificationService(emailService);
        Member member = new Member();
        member.setFirstName("Ada");
        member.setLastName("Lovelace");

        service.sendVerificationCode(member, "123456", 15);

        ArgumentCaptor<EmailDispatchRequest> requestCaptor = ArgumentCaptor.forClass(EmailDispatchRequest.class);
        verify(emailService).sendAndLogEmail(requestCaptor.capture());
        EmailDispatchRequest request = requestCaptor.getValue();
        assertEquals(member, request.member());
        assertEquals(EmailTemplateType.OTC_VERIFICATION, request.templateType());
        assertEquals("[CIS SAS] Email Verification Code", request.subject());
        assertTrue(request.body().contains("Dear Ada Lovelace"));
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
        Applicant applicant = new Applicant();
        applicant.setFullName("Applicant Name");
        applicant.setDateOfBirth(LocalDate.of(2018, 1, 1));
        ProgrammeLevel level = new ProgrammeLevel();
        level.setLevelCode(LevelCode.S1);
        Application application = new Application();
        application.setApplicationNumber("APP-123");
        application.setProgrammeLevel(level);

        service.notifyAdminOfIdentityReview(member, applicant, application, "2026/27");

        ArgumentCaptor<EmailDispatchRequest> requestCaptor = ArgumentCaptor.forClass(EmailDispatchRequest.class);
        verify(emailService).sendAndLogEmail(requestCaptor.capture());
        EmailDispatchRequest request = requestCaptor.getValue();
        assertEquals(EmailTemplateType.IDENTITY_REVIEW_ALERT, request.templateType());
        assertEquals("admissions@example.com", request.recipientEmail());
        assertEquals(application, request.application());
    }

    @Test
    void logsMissingAdminAddressInsteadOfBlockingSubmissionNotificationFlow() {
        EmailService emailService = mock(EmailService.class);
        NotificationService service = new NotificationService(emailService);
        ReflectionTestUtils.setField(service, "adminNotificationEmail", "");

        Application application = new Application();
        application.setApplicationNumber("APP-123");

        service.notifyAdminOfIdentityReview(new Member(), new Applicant(), application, "2026/27");

        verifyNoInteractions(emailService);
    }
}
