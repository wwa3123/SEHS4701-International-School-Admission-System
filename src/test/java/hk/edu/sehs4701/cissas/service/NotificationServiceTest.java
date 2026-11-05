package hk.edu.sehs4701.cissas.service;

import hk.edu.sehs4701.cissas.dto.request.EmailDispatchRequest;
import hk.edu.sehs4701.cissas.entity.Member;
import hk.edu.sehs4701.cissas.enums.EmailTemplateType;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

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
}
