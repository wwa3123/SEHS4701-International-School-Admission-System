package hk.edu.sehs4701.cissas.dto.request;

import hk.edu.sehs4701.cissas.entity.Application;
import hk.edu.sehs4701.cissas.entity.Member;
import hk.edu.sehs4701.cissas.enums.EmailTemplateType;

public record EmailDispatchRequest(
        Member member,
        Application application,
        EmailTemplateType templateType,
        String applicantName,
        String applicationNumber,
        String actionRequired,
        String subject,
        String body,
        String recipientEmail
) {
    public EmailDispatchRequest(
            Member member,
            Application application,
            EmailTemplateType templateType,
            String applicantName,
            String applicationNumber,
            String actionRequired,
            String subject,
            String body) {
        this(member, application, templateType, applicantName, applicationNumber,
                actionRequired, subject, body, member.getEmail());
    }
}
