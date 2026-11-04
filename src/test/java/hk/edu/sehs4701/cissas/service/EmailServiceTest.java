package hk.edu.sehs4701.cissas.service;

import hk.edu.sehs4701.cissas.dto.request.EmailDispatchRequest;
import hk.edu.sehs4701.cissas.entity.EmailLog;
import hk.edu.sehs4701.cissas.entity.Member;
import hk.edu.sehs4701.cissas.enums.EmailTemplateType;
import hk.edu.sehs4701.cissas.event.EmailSendRequestedEvent;
import hk.edu.sehs4701.cissas.repository.EmailLogRepository;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
}
