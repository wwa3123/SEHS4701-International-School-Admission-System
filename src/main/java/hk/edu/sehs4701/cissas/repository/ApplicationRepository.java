package hk.edu.sehs4701.cissas.repository;

import hk.edu.sehs4701.cissas.entity.Application;
import hk.edu.sehs4701.cissas.enums.ApplicationStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.Optional;

public interface ApplicationRepository extends JpaRepository<Application, Long> {
    Optional<Application>
    findFirstByApplicant_IdentityDocuments_DocumentNumberHashAndProgrammeLevel_IdAndProgrammeLevel_AcademicYear_IdAndStatusNotOrderBySubmittedAtDesc(
            String documentNumberHash,
            Long programmeLevelId,
            Long academicYearId,
            ApplicationStatus status
    );

    Optional<Application>
    findFirstByApplicant_IdAndProgrammeLevel_IdAndProgrammeLevel_AcademicYear_IdAndStatusNotOrderBySubmittedAtDesc(
            Long applicantId,
            Long programmeLevelId,
            Long academicYearId,
            ApplicationStatus status
    );

    boolean existsByApplicant_IdInAndProgrammeLevel_IdAndProgrammeLevel_AcademicYear_IdAndStatusNot(
            Collection<Long> applicantIds,
            Long programmeLevelId,
            Long academicYearId,
            ApplicationStatus status
    );
}
