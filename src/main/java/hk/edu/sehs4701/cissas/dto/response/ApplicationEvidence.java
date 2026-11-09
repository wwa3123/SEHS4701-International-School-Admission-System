package hk.edu.sehs4701.cissas.dto.response;

import hk.edu.sehs4701.cissas.enums.IdentityDocumentType;

import java.time.LocalDate;

public record ApplicationEvidence(
        String applicationNumber,
        String fullName,
        LocalDate dateOfBirth,
        String gender,
        String phone,
        String previousSchool,
        IdentityDocumentType documentType,
        String documentNumber,
        String issuingCountry,
        String academicYear,
        String status) {
}
