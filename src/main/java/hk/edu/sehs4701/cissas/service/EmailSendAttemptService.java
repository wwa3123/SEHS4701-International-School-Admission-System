package hk.edu.sehs4701.cissas.service;

import hk.edu.sehs4701.cissas.entity.EmailLog;
import hk.edu.sehs4701.cissas.enums.EmailDeliveryStatus;
import hk.edu.sehs4701.cissas.event.EmailSendRequestedEvent;
import hk.edu.sehs4701.cissas.repository.EmailLogRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
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
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.LocalDateTime;

@Service
public class EmailSendAttemptService {
    private static final Logger log = LoggerFactory.getLogger(EmailSendAttemptService.class);

    private final EmailLogRepository emailLogRepository;
    private final JavaMailSender mailSender;

    @Value("${spring.mail.username}")
    private String fromAddress;

    @Autowired
    public EmailSendAttemptService(EmailLogRepository emailLogRepository, JavaMailSender mailSender) {
        this.emailLogRepository = emailLogRepository;
        this.mailSender = mailSender;
    }

    @Async
    @Retryable(
            retryFor = { MailException.class },
            maxAttemptsExpression = "${cissas.email.max-retries}",
            backoff = @Backoff(delay = 5000)
    )
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void attemptSmtpSend(EmailSendRequestedEvent event) {
        Long emailLogId = event.emailLogId();
        EmailLog emailLog = emailLogRepository.findById(emailLogId)
                .orElseThrow(() -> new IllegalArgumentException("Email log not found: " + emailLogId));

        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(fromAddress);
            message.setTo(emailLog.getRecipientEmail());
            message.setSubject(emailLog.getSubject());
            message.setText(event.transientBody() == null ? emailLog.getBody() : event.transientBody());

            mailSender.send(message);

            emailLog.setStatus(EmailDeliveryStatus.SENT);
            emailLog.setSentAt(LocalDateTime.now());
            emailLog.setErrorMessage(null);
            log.info("Dispatched email template={} to={} retryCount={}",
                    emailLog.getTemplateType(), emailLog.getRecipientEmail(), emailLog.getRetryCount());
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
        }
    }

    @Recover
    @Transactional
    public void recover(MailException ex, EmailSendRequestedEvent event) {
        Long emailLogId = event.emailLogId();
        EmailLog emailLog = emailLogRepository.findById(emailLogId)
                .orElseThrow(() -> new IllegalArgumentException("Email log not found: " + emailLogId));
        emailLog.setStatus(EmailDeliveryStatus.FAILED);
        String err = ex.getMessage() != null ? ex.getMessage() : ex.getClass().getSimpleName();
        emailLog.setErrorMessage(err.length() > 500 ? err.substring(0, 500) : err);
        log.warn("Failed email after retries template={} to={} retryCount={} error={}",
                emailLog.getTemplateType(), emailLog.getRecipientEmail(),
                emailLog.getRetryCount(), emailLog.getErrorMessage());
    }
}
