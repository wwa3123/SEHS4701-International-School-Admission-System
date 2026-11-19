package hk.edu.sehs4701.cissas.dto.response;

import hk.edu.sehs4701.cissas.enums.LevelCode;

import java.time.LocalDate;

public record ProgrammeLevelResponse(
        Long id,
        Long academicYearId,
        LevelCode levelCode,
        String programmeName,
        Integer seatQuota,
        boolean admissionOpen,
        LocalDate applicationStartDate,
        LocalDate applicationEndDate) {
}
