package hk.edu.sehs4701.cissas.dto.request;

import hk.edu.sehs4701.cissas.enums.ApplicationStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record SetApplicationResultRequest(
        @NotNull ApplicationStatus result,
        @Size(max = 255) String reason) {
}
