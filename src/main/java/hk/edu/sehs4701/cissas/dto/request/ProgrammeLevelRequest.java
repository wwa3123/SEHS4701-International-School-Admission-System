package hk.edu.sehs4701.cissas.dto.request;

import hk.edu.sehs4701.cissas.enums.LevelCode;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;

public record ProgrammeLevelRequest(
        @NotNull LevelCode levelCode,
        @NotBlank @Size(max = 120) String programmeName,
        @NotNull @Min(0) Integer seatQuota,
        @NotNull @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate applicationStartDate,
        @NotNull @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate applicationEndDate) {
}
