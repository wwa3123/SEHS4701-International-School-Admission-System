package hk.edu.sehs4701.cissas.dto.response;

import java.time.LocalDateTime;

public record Decision(
        String flaggedApplicationNumber,
        String candidateApplicationNumber,
        boolean samePerson,
        String reviewedBy,
        LocalDateTime reviewedAt,
        boolean current) {
}
