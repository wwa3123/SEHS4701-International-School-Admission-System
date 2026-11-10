package hk.edu.sehs4701.cissas.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

public record ExtendOfferDeadlineRequest(
        @NotNull LocalDateTime acceptanceDeadline,
        @NotBlank @Size(max = 255) String reason) {
}
