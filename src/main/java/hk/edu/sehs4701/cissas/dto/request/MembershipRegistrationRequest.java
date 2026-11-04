package hk.edu.sehs4701.cissas.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import hk.edu.sehs4701.cissas.enums.IdentityDocumentType;

public record MembershipRegistrationRequest(
        @NotBlank @Size(max = 120) String firstName,
        @NotBlank @Size(max = 120) String lastName,
        @NotBlank @Email @Size(max = 150) String email,
        @NotBlank @Size(max = 25) String phoneNumber,
        @NotNull IdentityDocumentType documentType,
        @NotBlank @Size(max = 512) String documentNumber,
        @Size(max = 2) String issuingCountry,
        @NotBlank @Size(min = 8, max = 256) String password
) {
}
