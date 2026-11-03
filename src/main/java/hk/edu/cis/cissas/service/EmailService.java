package hk.edu.cis.cissas.service;

import hk.edu.cis.cissas.enums.EmailDeliveryStatus;
import hk.edu.cis.cissas.entity.EmailLog;
import hk.edu.cis.cissas.event.EmailSendRequestedEvent;
import hk.edu.cis.cissas.repository.EmailLogRepository;
import hk.edu.cis.cissas.dto.request.EmailDispatchRequest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
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

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public EmailLog sendAndLogEmail(EmailDispatchRequest request) {
        EmailLog emailLog = new EmailLog();
        emailLog.setMember(request.member());
        emailLog.setApplication(request.application());
        emailLog.setTemplateType(request.templateType());
        emailLog.setRecipientEmail(request.member().getEmail()); // Always to registered email
        emailLog.setApplicantName(request.applicantName());
        emailLog.setApplicationNumber(request.applicationNumber());
        emailLog.setActionRequired(request.actionRequired());
        emailLog.setSubject(request.subject());
        emailLog.setBody(request.body());
        emailLog.setStatus(EmailDeliveryStatus.PENDING);
        emailLog.setRetryCount(0);
        emailLog.setErrorMessage(null);

        EmailLog savedLog = emailLogRepository.save(emailLog);
        eventPublisher.publishEvent(new EmailSendRequestedEvent(savedLog.getId()));
        return savedLog;
    }

    @Transactional
    public EmailLog manualResend(Long emailLogId) {
        EmailLog emailLog = emailLogRepository.findById(emailLogId)
                .orElseThrow(() -> new IllegalArgumentException("Email log not found: " + emailLogId));

        emailLog.setRetryCount(emailLog.getRetryCount() + 1);
        emailLog.setStatus(EmailDeliveryStatus.PENDING);
        emailLog.setErrorMessage(null);
        EmailLog savedLog = emailLogRepository.save(emailLog);
        eventPublisher.publishEvent(new EmailSendRequestedEvent(savedLog.getId()));
        return savedLog;
    }

    public String formatStandardBody(
            String memberName,
            String applicantName,
            String applicationNumber,
            String actionRequired,
            String messageContent) {
        return """
            Dear %s,

            %s

            ------------------------------------------------------------
            Applicant Name     : %s
            Application Number : %s
            Action Required    : %s
            ------------------------------------------------------------

            Best regards,
            Admissions Office
            Christian International School (CIS)
            """.formatted(memberName, messageContent, applicantName, applicationNumber, actionRequired);
    }


}
