package hk.edu.sehs4701.cissas.repository;

import hk.edu.sehs4701.cissas.entity.ProgrammeLevel;
import hk.edu.sehs4701.cissas.enums.LevelCode;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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
    @Query("""
            select case when count(level) > 0 then true else false end
            from ProgrammeLevel level
            where level.academicYear.id = :academicYearId
              and level.applicationStartDate <= :date
              and level.applicationEndDate >= :date
              and (level.levelCode <> :s6 or level.admissionOpen = true)
            """)
    boolean existsOpenForApplication(@Param("academicYearId") Long academicYearId,
                                     @Param("date") LocalDate date, @Param("s6") LevelCode s6);
}
