package hk.edu.sehs4701.cissas.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record ApplicationDuplicateReviewRequest(
        @NotBlank String duplicateApplicationNumber,
        @NotBlank String matchingApplicationNumber,
        @NotNull Boolean samePerson) {
}
