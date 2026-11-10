package hk.edu.sehs4701.cissas.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.NotBlank;

public record OfferDecisionRequest(@NotNull Decision decision, @NotBlank String confirmationToken) {
    public enum Decision { Accept, Reject }
}
