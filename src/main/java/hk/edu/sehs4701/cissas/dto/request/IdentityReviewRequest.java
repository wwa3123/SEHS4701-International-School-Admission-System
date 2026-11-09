package hk.edu.sehs4701.cissas.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record IdentityReviewRequest(
        @NotBlank String flaggedApplicationNumber,
        @NotBlank String candidateApplicationNumber,
        @NotNull Boolean samePerson) {
}
