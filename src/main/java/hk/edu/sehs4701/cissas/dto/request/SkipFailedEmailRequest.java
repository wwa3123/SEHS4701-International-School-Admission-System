package hk.edu.sehs4701.cissas.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SkipFailedEmailRequest(@NotBlank @Size(max = 255) String reason) {
}
