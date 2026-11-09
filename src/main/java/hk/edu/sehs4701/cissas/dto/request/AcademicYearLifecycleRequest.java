package hk.edu.sehs4701.cissas.dto.request;

import hk.edu.sehs4701.cissas.enums.AcademicYearStatus;
import jakarta.validation.constraints.NotNull;

public record AcademicYearLifecycleRequest(@NotNull AcademicYearStatus status) {
}
