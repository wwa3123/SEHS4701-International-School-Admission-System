package hk.edu.sehs4701.cissas.dto.request;

import hk.edu.sehs4701.cissas.enums.IdentityDocumentType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record MemberIdentityDocumentRequest(
        @NotNull IdentityDocumentType documentType,
        @NotBlank @Size(max = 512) String documentNumber,
        @Size(max = 2) String issuingCountry) {
}
