package hk.edu.sehs4701.cissas.service;

import hk.edu.sehs4701.cissas.entity.EmailLog;
import hk.edu.sehs4701.cissas.enums.EmailDeliveryStatus;
import hk.edu.sehs4701.cissas.enums.EmailTemplateType;
import hk.edu.sehs4701.cissas.event.EmailSendRequestedEvent;
import hk.edu.sehs4701.cissas.repository.EmailLogRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Stream;

@Slf4j
@Service
public class EmailSendAttemptService {
    static final List<EmailTemplateType> APPLICATION_RESULTS = List.of(
            EmailTemplateType.RESULT_ANNOUNCEMENT, EmailTemplateType.WAIT_TO_SUCCESS,
            EmailTemplateType.CORRECTED_RESULT_ANNOUNCEMENT, EmailTemplateType.APPLICATION_CANCELLATION,
            EmailTemplateType.OFFER_DEADLINE_EXTENSION);
    static final List<EmailTemplateType> RECOVERABLE_APPLICATION_EMAILS =
            Stream.concat(APPLICATION_RESULTS.stream(), Stream.of(EmailTemplateType.APPLICATION_DUPLICATE_ALERT)).toList();
    static final List<EmailDeliveryStatus> UNSENT = List.of(EmailDeliveryStatus.PENDING, EmailDeliveryStatus.FAILED);

    private final EmailLogRepository emailLogRepository;
    private final JavaMailSender mailSender;
    private final ApplicationEventPublisher events;

    @Value("${spring.mail.username}")
    private String fromAddress;

    @Value("${cissas.otc.expiry-minutes:15}")
    private int otcExpiryMinutes;

    @Autowired
    public EmailSendAttemptService(EmailLogRepository emailLogRepository, JavaMailSender mailSender,
                                   ApplicationEventPublisher events) {
        this.emailLogRepository = emailLogRepository;
        this.mailSender = mailSender;
        this.events = events;
    }

    @Async
    @Retryable(
            retryFor = { MailException.class },
            maxAttemptsExpression = "${cissas.email.max-retries}",
            backoff = @Backoff(delay = 5000)
    )
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW, isolation = Isolation.READ_COMMITTED)
    public void attemptSmtpSend(EmailSendRequestedEvent event) {
        Long emailLogId = event.emailLogId();
        Long applicationId = emailLogRepository.findApplicationId(emailLogId).orElse(null);
        if (applicationId != null) {
            emailLogRepository.findFirstApplicationEmailId(applicationId, APPLICATION_RESULTS)
                    .ifPresent(id -> emailLogRepository.findLockedById(id)
                            .orElseThrow(() -> new IllegalStateException("Email log not found: " + id)));
        }
        EmailLog emailLog = emailLogRepository.findLockedById(emailLogId)
                .orElseThrow(() -> new IllegalArgumentException("Email log not found: " + emailLogId));
        boolean ordered = applicationId != null && APPLICATION_RESULTS.contains(emailLog.getTemplateType());
        if (emailLog.getStatus() != EmailDeliveryStatus.PENDING
                && !(emailLog.getStatus() == EmailDeliveryStatus.FAILED
                && RECOVERABLE_APPLICATION_EMAILS.contains(emailLog.getTemplateType()))) return;
        if (emailLog.getTemplateType() == EmailTemplateType.OTC_VERIFICATION
                && emailLog.getCreatedAt().plusMinutes(otcExpiryMinutes).isBefore(LocalDateTime.now())) {
            emailLog.setStatus(EmailDeliveryStatus.FAILED);
            emailLog.setErrorMessage("Verification code expired before delivery; request a new code.");
            return;
        }
        if (ordered) {
            EmailLog firstUnsent = emailLogRepository
                    .findFirstByApplication_IdAndTemplateTypeInAndStatusInOrderByIdAsc(
                            applicationId, APPLICATION_RESULTS, UNSENT)
                    .orElseThrow(() -> new IllegalStateException("Pending email missing from delivery queue"));
            if (!firstUnsent.getId().equals(emailLogId)) return;
        }

        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(fromAddress);
            message.setTo(emailLog.getRecipientEmail());
            message.setSubject(emailLog.getSubject());
            message.setText(event.transientBody() == null ? emailLog.getBody() : event.transientBody());

            mailSender.send(message);
        } catch (MailException ex) {
            String err = ex.getMessage() != null ? ex.getMessage() : ex.getClass().getSimpleName();
            emailLog.setErrorMessage(err.length() > 500 ? err.substring(0, 500) : err);
            log.warn("Retrying email template={} to={} retryCount={} error={}",
                    emailLog.getTemplateType(), emailLog.getRecipientEmail(),
                    emailLog.getRetryCount(), emailLog.getErrorMessage());
            throw ex;
        } catch (Exception ex) {
            emailLog.setStatus(EmailDeliveryStatus.FAILED);
            String err = ex.getMessage() != null ? ex.getMessage() : ex.getClass().getSimpleName();
            emailLog.setErrorMessage(err.length() > 500 ? err.substring(0, 500) : err);
            log.warn("Failed email template={} to={} retryCount={} error={}",
                    emailLog.getTemplateType(), emailLog.getRecipientEmail(),
                    emailLog.getRetryCount(), emailLog.getErrorMessage());
            return;
        }
        emailLog.setStatus(EmailDeliveryStatus.SENT);
        emailLog.setSentAt(LocalDateTime.now());
        emailLog.setErrorMessage(null);
        if (ordered) {
            emailLogRepository.flush();
            emailLogRepository.findFirstByApplication_IdAndTemplateTypeInAndStatusInOrderByIdAsc(
                            applicationId, APPLICATION_RESULTS, UNSENT)
                    .filter(next -> next.getStatus() == EmailDeliveryStatus.PENDING)
                    .ifPresent(next -> events.publishEvent(new EmailSendRequestedEvent(next.getId(), null)));
        }
        log.info("Dispatched email template={} to={} retryCount={}",
                emailLog.getTemplateType(), emailLog.getRecipientEmail(), emailLog.getRetryCount());
    }

    @Recover
    public void recover(MailException ex, EmailSendRequestedEvent event) {
        Long emailLogId = event.emailLogId();
        String err = ex.getMessage() != null ? ex.getMessage() : ex.getClass().getSimpleName();
        String message = err.length() > 500 ? err.substring(0, 500) : err;
        emailLogRepository.failIfPending(emailLogId, EmailDeliveryStatus.PENDING,
                EmailDeliveryStatus.FAILED, message);
        log.warn("Failed email after retries emailLogId={} error={}", emailLogId, message);
    }
}
