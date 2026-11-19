package hk.edu.sehs4701.cissas.dto.response;

import hk.edu.sehs4701.cissas.enums.IdentityDocumentType;

public record MemberIdentityDocument(
        Long id,
        IdentityDocumentType documentType,
        String maskedDocumentNumber,
        String issuingCountry,
        boolean primary) {
}
