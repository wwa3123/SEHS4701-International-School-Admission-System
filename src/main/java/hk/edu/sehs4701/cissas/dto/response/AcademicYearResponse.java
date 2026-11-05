package hk.edu.sehs4701.cissas.dto.response;

import hk.edu.sehs4701.cissas.enums.AcademicYearStatus;

import java.time.LocalDate;

public record AcademicYearResponse(
        Long id,
        String name,
        LocalDate startDate,
        LocalDate endDate,
        AcademicYearStatus status
) {
}
