package hk.edu.cis.cissas.dto.request;

import hk.edu.cis.cissas.entity.Application;
import hk.edu.cis.cissas.entity.Member;
import hk.edu.cis.cissas.enums.EmailTemplateType;

public record EmailDispatchRequest(
        Member member,
        Application application,
        EmailTemplateType templateType,
        String applicantName,
        String applicationNumber,
        String actionRequired,
        String subject,
        String body
) {}
