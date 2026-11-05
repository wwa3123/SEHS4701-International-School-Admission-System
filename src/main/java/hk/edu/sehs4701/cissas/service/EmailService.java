package hk.edu.sehs4701.cissas.service;

import hk.edu.sehs4701.cissas.enums.EmailDeliveryStatus;
import hk.edu.sehs4701.cissas.entity.EmailLog;
import hk.edu.sehs4701.cissas.event.EmailSendRequestedEvent;
import hk.edu.sehs4701.cissas.repository.EmailLogRepository;
import hk.edu.sehs4701.cissas.dto.request.EmailDispatchRequest;
import hk.edu.sehs4701.cissas.enums.EmailTemplateType;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EmailService {
    private final EmailLogRepository emailLogRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Autowired
    public EmailService(
            EmailLogRepository emailLogRepository,
            ApplicationEventPublisher eventPublisher) {
        this.emailLogRepository = emailLogRepository;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public EmailLog sendAndLogEmail(EmailDispatchRequest request) {
        EmailLog emailLog = new EmailLog();
        emailLog.setMember(request.member());
        emailLog.setApplication(request.application());
        emailLog.setTemplateType(request.templateType());
        emailLog.setRecipientEmail(request.member().getEmail());
        emailLog.setApplicantName(request.applicantName());
        emailLog.setApplicationNumber(request.applicationNumber());
        emailLog.setActionRequired(request.actionRequired());
        emailLog.setSubject(request.subject());
        boolean containsVerificationCode = request.templateType() == EmailTemplateType.OTC_VERIFICATION;
        emailLog.setBody(containsVerificationCode ? "[Verification code email body redacted]" : request.body());
        emailLog.setStatus(EmailDeliveryStatus.PENDING);
        emailLog.setRetryCount(0);
        emailLog.setErrorMessage(null);

        EmailLog savedLog = emailLogRepository.save(emailLog);
        eventPublisher.publishEvent(new EmailSendRequestedEvent(
                savedLog.getId(),
                containsVerificationCode ? request.body() : null
        ));
        return savedLog;
    }

    @Transactional
    public EmailLog manualResend(Long emailLogId) {
        EmailLog emailLog = emailLogRepository.findById(emailLogId)
                .orElseThrow(() -> new IllegalArgumentException("Email log not found: " + emailLogId));
        if (emailLog.getTemplateType() == EmailTemplateType.OTC_VERIFICATION) {
            throw new IllegalStateException("Request a new verification code instead of resending an old code.");
        }

        emailLog.setRetryCount(emailLog.getRetryCount() + 1);
        emailLog.setStatus(EmailDeliveryStatus.PENDING);
        emailLog.setErrorMessage(null);
        EmailLog savedLog = emailLogRepository.save(emailLog);
        eventPublisher.publishEvent(new EmailSendRequestedEvent(savedLog.getId(), null));
        return savedLog;
    }




}
