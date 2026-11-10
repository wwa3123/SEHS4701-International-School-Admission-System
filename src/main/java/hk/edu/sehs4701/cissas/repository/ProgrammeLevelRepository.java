package hk.edu.sehs4701.cissas.repository;

import hk.edu.sehs4701.cissas.entity.ProgrammeLevel;
import hk.edu.sehs4701.cissas.enums.LevelCode;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.List;
import java.time.LocalDate;

public interface ProgrammeLevelRepository extends JpaRepository<ProgrammeLevel, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<ProgrammeLevel> findByLevelCodeAndAcademicYear_Id(LevelCode code, Long academicYearId);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<ProgrammeLevel> findLockedById(Long id);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<ProgrammeLevel> findByIdAndAcademicYear_Id(Long id, Long academicYearId);
    List<ProgrammeLevel> findAllByAcademicYear_IdOrderByLevelCodeAsc(Long academicYearId);
    boolean existsByLevelCodeAndAcademicYear_Id(LevelCode code, Long academicYearId);
    boolean existsByAcademicYear_IdAndApplicationStartDateLessThanEqualAndApplicationEndDateGreaterThanEqual(
            Long academicYearId, LocalDate startDate, LocalDate endDate);
}
