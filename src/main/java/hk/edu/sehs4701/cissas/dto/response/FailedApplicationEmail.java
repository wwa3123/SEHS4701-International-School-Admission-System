package hk.edu.sehs4701.cissas.dto.response;

import hk.edu.sehs4701.cissas.enums.EmailTemplateType;

public record FailedApplicationEmail(Long id, String applicationNumber, EmailTemplateType templateType,
                                     String errorMessage) {
}
