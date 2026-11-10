package hk.edu.sehs4701.cissas.repository;

import hk.edu.sehs4701.cissas.entity.Application;
import hk.edu.sehs4701.cissas.enums.ApplicationStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ApplicationRepository extends JpaRepository<Application, Long> {
    Optional<Application> findByApplicationNumber(String applicationNumber);

    @Query("select a.programmeLevel.id from Application a where a.applicationNumber = :number")
    Optional<Long> findProgrammeLevelId(String number);

    @Query("select a.programmeLevel.id from Application a where a.applicationNumber = :number and a.member.id = :memberId")
    Optional<Long> findProgrammeLevelIdForMember(String number, Long memberId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Application> findLockedByApplicationNumber(String applicationNumber);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Application> findLockedByApplicationNumberAndMember_Id(String applicationNumber, Long memberId);

    long countByProgrammeLevel_IdAndStatusIn(Long programmeLevelId, Collection<ApplicationStatus> statuses);

    List<Application> findAllByProgrammeLevel_IdAndStatus(Long programmeLevelId, ApplicationStatus status);

    @Query("""
        select a from Application a
        where (:programmeLevelId is null or a.programmeLevel.id = :programmeLevelId)
          and (:status is null
            or (:status = hk.edu.sehs4701.cissas.enums.ApplicationStatus.Rejected
                and (a.status = :status or (a.status = hk.edu.sehs4701.cissas.enums.ApplicationStatus.Success
                     and a.acceptanceDeadline <= :now)))
            or (:status = hk.edu.sehs4701.cissas.enums.ApplicationStatus.Success
                and a.status = :status and (a.acceptanceDeadline is null or a.acceptanceDeadline > :now))
            or (:status not in (hk.edu.sehs4701.cissas.enums.ApplicationStatus.Rejected,
                                hk.edu.sehs4701.cissas.enums.ApplicationStatus.Success) and a.status = :status))
        order by a.id
        """)
    Slice<Application> findForAdministration(Long programmeLevelId, ApplicationStatus status, LocalDateTime now, Pageable pageable);

    Slice<Application> findAllByMember_IdOrderByIdAsc(Long memberId, Pageable pageable);

    List<Application> findAllByProgrammeLevel_IdAndStatusAndAcceptanceDeadlineLessThanEqual(
            Long programmeLevelId, ApplicationStatus status, LocalDateTime deadline);

    @Query("""
        select a from Application a where a.status = hk.edu.sehs4701.cissas.enums.ApplicationStatus.Success
          and a.acceptanceDeadline <= :deadline
          and (:afterId is null or a.id > :afterId)
        order by a.id
        """)
    Slice<Application> findExpiredOffers(java.time.LocalDateTime deadline, Long afterId, Pageable pageable);

    @Query("""
        select f, c from Application f, Application c
        where f.id <> c.id
          and f.manualIdentityReviewRequired = true
          and f.status = hk.edu.sehs4701.cissas.enums.ApplicationStatus.Pending
          and c.status <> hk.edu.sehs4701.cissas.enums.ApplicationStatus.Cancelled
          and (:afterFlagged is null or f.id > :afterFlagged
               or (f.id = :afterFlagged and c.id > :afterCandidate))
          and f.applicantSnapshot.dateOfBirth = c.applicantSnapshot.dateOfBirth
          and f.applicantSnapshot.nameHash = c.applicantSnapshot.nameHash
          and f.applicant.id <> c.applicant.id
          and not exists (select r.id from IdentityReview r where r.currentMarker = 1
            and r.samePerson = false
            and ((r.firstApplicant = f.applicant and r.secondApplicant = c.applicant)
              or (r.secondApplicant = f.applicant and r.firstApplicant = c.applicant)))
        order by f.id, c.id
        """)
    Slice<Object[]> findPotentialReviewPairs(Long afterFlagged, Long afterCandidate, Pageable pageable);

    List<Application> findAllByStatusNotAndApplicantSnapshot_DateOfBirthAndApplicantSnapshot_NameHash(
            ApplicationStatus status, LocalDate dateOfBirth, byte[] nameHash);


    Optional<Application>
    findFirstByApplicant_IdentityDocuments_DocumentNumberHashAndProgrammeLevel_IdAndAcademicYear_IdAndStatusNotOrderBySubmittedAtDesc(
            String documentNumberHash,
            Long programmeLevelId,
            Long academicYearId,
            ApplicationStatus status
    );

}
