package hk.edu.sehs4701.cissas.service;

import hk.edu.sehs4701.cissas.dto.request.EmailDispatchRequest;
import hk.edu.sehs4701.cissas.entity.Application;
import hk.edu.sehs4701.cissas.entity.Member;
import hk.edu.sehs4701.cissas.entity.ApplicantSnapshot;
import hk.edu.sehs4701.cissas.enums.ApplicationStatus;
import hk.edu.sehs4701.cissas.enums.EmailTemplateType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

@Slf4j
@Service
public class NotificationService {
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
    public void sendPasswordResetCode(Member member, String rawSixDigitCode, int expiryMinutes) {
        String actionRequired = "Enter the one-time code within " + expiryMinutes
                + " minutes to reset your SAS password.";
        String body = formatStandardBody(member.getFirstName(), "N/A", "N/A (Password Reset)", actionRequired,
                "Your one-time 6-digit password reset code is: " + rawSixDigitCode
                        + "\nThis code expires in " + expiryMinutes + " minutes.");
        emailService.sendAndLogEmail(new EmailDispatchRequest(member, null, EmailTemplateType.PASSWORD_RESET_CODE,
                "N/A", "N/A (Password Reset)", actionRequired, "[CIS SAS] Password Reset Code", body));
    }

    @Transactional
    public void notifyAdminOfPossibleDuplicate(
            Member member,
            Application application,
            String academicYearName) {
        if (adminNotificationEmail == null || adminNotificationEmail.isBlank()) {
            log.error("Cannot notify admin about possible duplicate {}: cissas.admin.notification-email is not configured",
                    application.getApplicationNumber());
            return;
        }

        String recipient = adminNotificationEmail.trim().toLowerCase(Locale.ROOT);
        String actionRequired = "Contact the member to establish whether the records are the same person.";
        ApplicantSnapshot applicant = application.getApplicantSnapshot();
        String body = formatStandardBody(
                "Admissions Team",
                applicant.getFullName(),
                application.getApplicationNumber(),
                actionRequired,
                "A possible duplicate was flagged because another application has the "
                        + "same applicant name and date of birth."
                        + "\nAcademic Year: " + academicYearName
                        + "\nProgramme Level: " + application.getProgrammeLevel().getLevelCode()
                        + "\nApplicant Date of Birth: " + applicant.getDateOfBirth()
                        + "\nMember Email: " + member.getEmail()
                        + "\nApplicant Phone: " + applicant.getPhone()
        );

        emailService.sendAndLogEmail(new EmailDispatchRequest(
                member,
                application,
                EmailTemplateType.APPLICATION_DUPLICATE_ALERT,
                applicant.getFullName(),
                application.getApplicationNumber(),
                actionRequired,
                "[CIS SAS] Possible Duplicate Application",
                body,
                recipient
        ));
    }

    @Transactional
    public void notifyApplicationAcknowledged(Member member, Application application) {
        ApplicantSnapshot applicant = application.getApplicantSnapshot();
        String actionRequired = "Log in to SAS to track your application status.";
        String body = formatStandardBody(member.getFirstName(), applicant.getFullName(),
                application.getApplicationNumber(), actionRequired,
                "Your application has been received successfully.");
        emailService.sendAndLogEmail(new EmailDispatchRequest(member, application,
                EmailTemplateType.APPLICATION_ACKNOWLEDGEMENT, applicant.getFullName(),
                application.getApplicationNumber(), actionRequired,
                "[CIS SAS] Application Acknowledgement", body));
    }

    @Transactional
    public void notifyApplicationResult(Application application, boolean corrected, String reason,
                                        boolean promotedFromWait) {
        ApplicantSnapshot applicant = application.getApplicantSnapshot();
        ApplicationStatus result = application.getStatus();
        String actionRequired = "Log in to SAS to check your current application status and any action required.";
        String message = "Result released: " + result
                + "\nProgramme Level: " + application.getProgrammeLevel().getProgrammeName()
                + "\nApplication Number: " + application.getApplicationNumber()
                + (result == ApplicationStatus.Success
                ? "\nSuccess means an offer. If it is still awaiting your response, accept or reject it in SAS before the current deadline."
                : "")
                + (application.getAcceptanceDeadline() == null ? "" :
                "\nAcceptance Deadline at release: " + application.getAcceptanceDeadline()
                        + "\nThe deadline or result may have changed since this notice was issued.")
                + (corrected ? "\nThis is a corrected result. Reason: " + reason : "");
        String body = formatStandardBody(application.getMember().getFirstName(), applicant.getFullName(),
                application.getApplicationNumber(), actionRequired, message);
        EmailTemplateType template = corrected ? EmailTemplateType.CORRECTED_RESULT_ANNOUNCEMENT
                : promotedFromWait ? EmailTemplateType.WAIT_TO_SUCCESS : EmailTemplateType.RESULT_ANNOUNCEMENT;
        emailService.sendAndLogEmail(new EmailDispatchRequest(
                application.getMember(), application, template, applicant.getFullName(),
                application.getApplicationNumber(), actionRequired,
                corrected ? "[CIS SAS] Corrected Application Result" : "[CIS SAS] Application Result",
                body));
    }

    @Transactional
    public void notifyOfferReminder(Application application) {
        ApplicantSnapshot applicant = application.getApplicantSnapshot();
        String actionRequired = "Log in to SAS now to accept or reject your offer before the deadline.";
        String body = formatStandardBody(application.getMember().getFirstName(), applicant.getFullName(),
                application.getApplicationNumber(), actionRequired,
                "Your offer acceptance deadline is within 48 hours: " + application.getAcceptanceDeadline() + "."
                        + "\nProgramme Level: " + application.getProgrammeLevel().getProgrammeName()
                        + "\nCheck your current deadline in SAS in case it has changed.");
        emailService.sendAndLogEmail(new EmailDispatchRequest(
                application.getMember(), application, EmailTemplateType.OFFER_48_HOUR_REMINDER,
                applicant.getFullName(), application.getApplicationNumber(), actionRequired,
                "[CIS SAS] Offer Response Deadline Reminder", body));
    }

    @Transactional
    public void notifyOfferDeadlineExtended(Application application) {
        ApplicantSnapshot applicant = application.getApplicantSnapshot();
        String actionRequired = "Log in to SAS to check your current application status and any action required.";
        String body = formatStandardBody(application.getMember().getFirstName(), applicant.getFullName(),
                application.getApplicationNumber(), actionRequired,
                "The acceptance deadline for your offer was extended."
                        + "\nProgramme Level: " + application.getProgrammeLevel().getProgrammeName()
                        + "\nExtended Acceptance Deadline: " + application.getAcceptanceDeadline()
                        + "\nThe deadline or result may have changed since this notice was issued.");
        emailService.sendAndLogEmail(new EmailDispatchRequest(
                application.getMember(), application, EmailTemplateType.OFFER_DEADLINE_EXTENSION,
                applicant.getFullName(), application.getApplicationNumber(), actionRequired,
                "[CIS SAS] Offer Acceptance Deadline Extended", body));
    }

    @Transactional
    public void notifyApplicationCancelled(Application application) {
        notifyApplicationCancelled(application,
                "Your application has been cancelled after an administrator confirmed that it is a duplicate.");
    }

    @Transactional
    public void notifyMemberApplicationCancelled(Application application) {
        notifyApplicationCancelled(application,
                "Your application has been cancelled at your request. This cancellation cannot be reversed.");
    }

    private void notifyApplicationCancelled(Application application, String message) {
        ApplicantSnapshot applicant = application.getApplicantSnapshot();
        String actionRequired = "No further action is required.";
        String body = formatStandardBody(application.getMember().getFirstName(), applicant.getFullName(),
                application.getApplicationNumber(), actionRequired,
                message);
        emailService.sendAndLogEmail(new EmailDispatchRequest(
                application.getMember(), application, EmailTemplateType.APPLICATION_CANCELLATION,
                applicant.getFullName(), application.getApplicationNumber(), actionRequired,
                "[CIS SAS] Application Cancellation Confirmation", body));
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
