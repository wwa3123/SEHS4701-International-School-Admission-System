package hk.edu.sehs4701.cissas.dto.response;

import hk.edu.sehs4701.cissas.enums.IdentityDocumentType;

import java.time.LocalDate;

public record ApplicationSummary(
        String applicationNumber,
        String fullName,
        LocalDate dateOfBirth,
        IdentityDocumentType documentType,
        String status) {
}
