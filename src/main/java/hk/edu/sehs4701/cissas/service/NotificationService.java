package hk.edu.sehs4701.cissas.service;

import hk.edu.sehs4701.cissas.dto.request.EmailDispatchRequest;
import hk.edu.sehs4701.cissas.entity.Application;
import hk.edu.sehs4701.cissas.entity.Member;
import hk.edu.sehs4701.cissas.entity.ApplicantSnapshot;
import hk.edu.sehs4701.cissas.enums.EmailTemplateType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

@Service
public class NotificationService {
    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);
    private final EmailService emailService;

    @Value("${cissas.admin.notification-email:}")
    private String adminNotificationEmail;

    @Autowired
    public NotificationService(EmailService emailService) {
        this.emailService = emailService;
    }

    @Transactional
    public void sendVerificationCode(Member member, String rawSixDigitCode, int otcExpiryMinutes) {
        String memberName = "New CIS Member";
        String applicantName = "N/A";
        String applicationNumber = "N/A (Membership Verification)";
        String actionRequired = "Enter the 6-digit verification code within " + otcExpiryMinutes
                + " minutes to verify your email and continue your CIS membership registration.";
        String body = formatStandardBody(
                memberName,
                applicantName,
                applicationNumber,
                actionRequired,
                "Your one-time 6-digit verification code is: " + rawSixDigitCode
                        + "\nThis code expires in " + otcExpiryMinutes + " minutes."
        );

        emailService.sendAndLogEmail(new EmailDispatchRequest(
                member,
                null,
                EmailTemplateType.OTC_VERIFICATION,
                applicantName,
                applicationNumber,
                actionRequired,
                "[CIS SAS] Email Verification Code",
                body
        ));
    }

    @Transactional
    public void notifyAdminOfIdentityReview(
            Member member,
            Application application,
            String academicYearName) {
        if (adminNotificationEmail == null || adminNotificationEmail.isBlank()) {
            log.error("Cannot notify admin about application {}: cissas.admin.notification-email is not configured",
                    application.getApplicationNumber());
            return;
        }

        String recipient = adminNotificationEmail.trim().toLowerCase(Locale.ROOT);
        String actionRequired = "Review the possible cross-document applicant match.";
        ApplicantSnapshot applicant = application.getApplicantSnapshot();
        String body = formatStandardBody(
                "Admissions Team",
                applicant.getFullName(),
                application.getApplicationNumber(),
                actionRequired,
                "A new application has been flagged for manual identity review because another "
                        + "non-cancelled application has the same applicant name and date of birth, "
                        + "but uses an HKID and passport combination."
                        + "\nAcademic Year: " + academicYearName
                        + "\nProgramme Level: " + application.getProgrammeLevel().getLevelCode()
                        + "\nApplicant Date of Birth: " + applicant.getDateOfBirth()
        );

        emailService.sendAndLogEmail(new EmailDispatchRequest(
                member,
                application,
                EmailTemplateType.IDENTITY_REVIEW_ALERT,
                applicant.getFullName(),
                application.getApplicationNumber(),
                actionRequired,
                "[CIS SAS] Application Requires Identity Review",
                body,
                recipient
        ));
    }

    private String formatStandardBody(
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
