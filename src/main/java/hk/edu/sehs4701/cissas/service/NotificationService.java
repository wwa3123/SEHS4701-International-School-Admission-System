package hk.edu.sehs4701.cissas.service;

import hk.edu.sehs4701.cissas.dto.request.EmailDispatchRequest;
import hk.edu.sehs4701.cissas.entity.Member;
import hk.edu.sehs4701.cissas.enums.EmailTemplateType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;


@Service
public class NotificationService {
    private final EmailService emailService;

    @Autowired
    public NotificationService(EmailService emailService) {
        this.emailService = emailService;
    }

    @Transactional
    public void sendVerificationCode(Member member, String rawSixDigitCode, int otcExpiryMinutes) {
        String memberName = member.getFirstName() + " " + member.getLastName();
        String applicantName = "N/A";
        String applicationNumber = "N/A (Membership Verification)";
        String actionRequired = "Enter the 6-digit verification code within " + otcExpiryMinutes
                + " minutes to activate your CIS membership account.";
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
