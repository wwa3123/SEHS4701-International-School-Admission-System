package hk.edu.sehs4701.cissas.repository;

import hk.edu.sehs4701.cissas.entity.Application;
import hk.edu.sehs4701.cissas.enums.ApplicationStatus;
import hk.edu.sehs4701.cissas.enums.IdentityDocumentType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ApplicationRepository extends JpaRepository<Application, Long> {
    Optional<Application> findByApplicationNumber(String applicationNumber);

    @Query("""
        select f, c from Application f, Application c
        where ((f.manualIdentityReviewRequired = true
                 and (c.manualIdentityReviewRequired = false or f.id > c.id))
            or (f.manualIdentityReviewRequired = false and c.manualIdentityReviewRequired = false
                and f.id > c.id
                and (f.status <> hk.edu.sehs4701.cissas.enums.ApplicationStatus.Cancelled
                     or c.status <> hk.edu.sehs4701.cissas.enums.ApplicationStatus.Cancelled)))
          and (:afterFlagged is null or f.id > :afterFlagged
               or (f.id = :afterFlagged and c.id > :afterCandidate))
          and f.programmeLevel.id = c.programmeLevel.id
          and f.academicYear.id = c.academicYear.id
          and f.applicantSnapshot.dateOfBirth = c.applicantSnapshot.dateOfBirth
          and f.applicantSnapshot.nameHash = c.applicantSnapshot.nameHash
          and ((f.applicantSnapshot.documentType = hk.edu.sehs4701.cissas.enums.IdentityDocumentType.HKID
                and c.applicantSnapshot.documentType = hk.edu.sehs4701.cissas.enums.IdentityDocumentType.PASSPORT)
            or (f.applicantSnapshot.documentType = hk.edu.sehs4701.cissas.enums.IdentityDocumentType.PASSPORT
                and c.applicantSnapshot.documentType = hk.edu.sehs4701.cissas.enums.IdentityDocumentType.HKID))
          and f.applicant.id <> c.applicant.id
          and not exists (select r.id from IdentityReview r where r.currentMarker = 1
            and ((r.firstApplicant = f.applicant and r.secondApplicant = c.applicant)
              or (r.secondApplicant = f.applicant and r.firstApplicant = c.applicant)))
        order by f.id, c.id
        """)
    Slice<Object[]> findPotentialReviewPairs(Long afterFlagged, Long afterCandidate, Pageable pageable);

    Optional<Application> findFirstByApplicant_IdInAndProgrammeLevel_IdAndAcademicYear_IdAndStatusNotOrderBySubmittedAtDesc(
            Collection<Long> applicantIds, Long programmeLevelId, Long academicYearId, ApplicationStatus status);

    Optional<Application>
    findFirstByApplicant_IdentityDocuments_DocumentNumberHashAndProgrammeLevel_IdAndAcademicYear_IdAndStatusNotOrderBySubmittedAtDesc(
            String documentNumberHash,
            Long programmeLevelId,
            Long academicYearId,
            ApplicationStatus status
    );

    List<Application>
    findAllByProgrammeLevel_IdAndAcademicYear_IdAndStatusNotAndApplicantSnapshot_DateOfBirthAndApplicantSnapshot_DocumentTypeAndApplicantSnapshot_NameHash(
            Long programmeLevelId,
            Long academicYearId,
            ApplicationStatus status,
            LocalDate dateOfBirth,
            IdentityDocumentType documentType,
            byte[] nameHash
    );
}
