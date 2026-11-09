package hk.edu.sehs4701.cissas.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record ProgrammeLevelUpdateRequest(
        @NotBlank @Size(max = 120) String programmeName,
        @NotNull @Min(0) Integer seatQuota,
        @NotNull LocalDate applicationStartDate,
        @NotNull LocalDate applicationEndDate) {
}
