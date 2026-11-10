package hk.edu.sehs4701.cissas.service;

import hk.edu.sehs4701.cissas.enums.EmailDeliveryStatus;
import hk.edu.sehs4701.cissas.entity.EmailLog;
import hk.edu.sehs4701.cissas.event.EmailSendRequestedEvent;
import hk.edu.sehs4701.cissas.repository.EmailLogRepository;
import hk.edu.sehs4701.cissas.dto.request.EmailDispatchRequest;
import hk.edu.sehs4701.cissas.enums.EmailTemplateType;
import hk.edu.sehs4701.cissas.dto.response.FailedApplicationEmail;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Slice;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneId;

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
    public void sendAndLogEmail(EmailDispatchRequest request) {
        EmailLog emailLog = new EmailLog();
        emailLog.setMember(request.member());
        emailLog.setApplication(request.application());
        emailLog.setTemplateType(request.templateType());
        emailLog.setRecipientEmail(request.recipientEmail());
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
    }

    @Transactional
    public void manualResend(Long emailLogId) {
        Long applicationId = emailLogRepository.findApplicationId(emailLogId).orElse(null);
        if (applicationId != null) {
            emailLogRepository.findFirstApplicationEmailId(applicationId, EmailSendAttemptService.APPLICATION_RESULTS)
                    .ifPresent(id -> emailLogRepository.findLockedById(id)
                            .orElseThrow(() -> new IllegalStateException("Email log not found: " + id)));
        }
        EmailLog emailLog = emailLogRepository.findLockedById(emailLogId)
                .orElseThrow(() -> new IllegalArgumentException("Email log not found: " + emailLogId));
        if (emailLog.getTemplateType() == EmailTemplateType.OTC_VERIFICATION) {
            throw new IllegalStateException("Request a new verification code instead of resending an old code.");
        }
        if (emailLog.getTemplateType() == EmailTemplateType.APPLICATION_DUPLICATE_ALERT
                && (emailLog.getStatus() == EmailDeliveryStatus.SENT
                || emailLog.getStatus() == EmailDeliveryStatus.SKIPPED)) {
            throw new IllegalStateException("A delivered or skipped alert cannot be resent.");
        }
        if (EmailSendAttemptService.APPLICATION_RESULTS.contains(emailLog.getTemplateType())
                && (emailLog.getStatus() == EmailDeliveryStatus.SENT
                || emailLog.getStatus() == EmailDeliveryStatus.SKIPPED
                || emailLogRepository.existsByApplication_IdAndIdGreaterThanAndTemplateTypeInAndStatus(
                        applicationId, emailLogId, EmailSendAttemptService.APPLICATION_RESULTS, EmailDeliveryStatus.SENT))) {
            throw new IllegalStateException("An outdated or delivered result email cannot be resent.");
        }

        emailLog.setRetryCount(emailLog.getRetryCount() + 1);
        emailLog.setStatus(EmailDeliveryStatus.PENDING);
        emailLog.setErrorMessage(null);
        EmailLog savedLog = emailLogRepository.save(emailLog);
        eventPublisher.publishEvent(new EmailSendRequestedEvent(savedLog.getId(), null));
    }

    @Transactional
    public void skipFailedApplicationEmail(Long emailLogId, Long adminId, String reason) {
        if (adminId == null || reason == null || reason.isBlank() || reason.length() > 255) {
            throw new IllegalArgumentException("Administrator and a reason of at most 255 characters are required");
        }
        Long applicationId = emailLogRepository.findApplicationId(emailLogId)
                .orElseThrow(() -> new IllegalArgumentException("Application email not found"));
        Long anchorId = emailLogRepository.findFirstApplicationEmailId(applicationId, EmailSendAttemptService.APPLICATION_RESULTS)
                .orElseThrow(() -> new IllegalArgumentException("Application email not found"));
        emailLogRepository.findLockedById(anchorId)
                .orElseThrow(() -> new IllegalStateException("Email queue not found"));
        EmailLog email = emailLogRepository.findLockedById(emailLogId)
                .orElseThrow(() -> new IllegalArgumentException("Application email not found"));
        if (!EmailSendAttemptService.APPLICATION_RESULTS.contains(email.getTemplateType())
                || email.getStatus() != EmailDeliveryStatus.FAILED) {
            throw new IllegalStateException("Only a failed application email can be skipped");
        }
        EmailLog head = emailLogRepository.findFirstByApplication_IdAndTemplateTypeInAndStatusInOrderByIdAsc(
                        applicationId, EmailSendAttemptService.APPLICATION_RESULTS, EmailSendAttemptService.UNSENT)
                .orElseThrow(() -> new IllegalStateException("Email queue not found"));
        if (!head.getId().equals(emailLogId)) {
            throw new IllegalStateException("Only the first unsent application email can be skipped");
        }
        email.setStatus(EmailDeliveryStatus.SKIPPED);
        email.setSkippedByAdminId(adminId);
        email.setSkippedAt(LocalDateTime.now(ZoneId.of("Asia/Hong_Kong")));
        email.setSkipReason(reason.trim());
        emailLogRepository.flush();
        EmailLog next = emailLogRepository.findFirstByApplication_IdAndTemplateTypeInAndStatusInOrderByIdAsc(
                        applicationId, EmailSendAttemptService.APPLICATION_RESULTS, EmailSendAttemptService.UNSENT)
                .orElseThrow(() -> new IllegalStateException("No later application email is waiting"));
        eventPublisher.publishEvent(new EmailSendRequestedEvent(next.getId(), null));
    }

    @Transactional(readOnly = true)
    public Slice<FailedApplicationEmail> failedApplicationEmails(int page) {
        if (page < 0) throw new IllegalArgumentException("Page must not be negative");
        return emailLogRepository.findAllByStatusAndTemplateTypeInOrderByIdAsc(
                EmailDeliveryStatus.FAILED, EmailSendAttemptService.RECOVERABLE_APPLICATION_EMAILS, PageRequest.of(page, 100))
                .map(email -> new FailedApplicationEmail(email.getId(), email.getApplicationNumber(),
                        email.getTemplateType(), email.getErrorMessage()));
    }

    @Transactional(readOnly = true)
    public Long retryUnsentApplicationEmails(Long afterId) {
        Slice<Long> emails = emailLogRepository.findUnsentApplicationEmailHeads(
                EmailSendAttemptService.UNSENT, EmailSendAttemptService.APPLICATION_RESULTS, afterId, PageRequest.of(0, 100));
        emails.forEach(id -> eventPublisher.publishEvent(new EmailSendRequestedEvent(id, null)));
        return emails.hasNext() ? emails.getContent().getLast() : null;
    }

    @Transactional(readOnly = true)
    public Long retryUnsentDuplicateAlerts(Long afterId) {
        Slice<EmailLog> emails = emailLogRepository.findAllByStatusInAndTemplateTypeAndIdGreaterThanOrderByIdAsc(
                EmailSendAttemptService.UNSENT, EmailTemplateType.APPLICATION_DUPLICATE_ALERT, afterId, PageRequest.of(0, 100));
        emails.forEach(email -> eventPublisher.publishEvent(new EmailSendRequestedEvent(email.getId(), null)));
        return emails.hasNext() ? emails.getContent().getLast().getId() : null;
    }
}
