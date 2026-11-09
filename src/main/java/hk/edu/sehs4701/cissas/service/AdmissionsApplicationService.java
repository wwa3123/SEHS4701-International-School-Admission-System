package hk.edu.sehs4701.cissas.service;

import hk.edu.sehs4701.cissas.dto.request.ApplicationSubmissionRequest;
import hk.edu.sehs4701.cissas.dto.response.AcademicYearResponse;
import hk.edu.sehs4701.cissas.entity.Application;
import hk.edu.sehs4701.cissas.entity.Applicant;
import hk.edu.sehs4701.cissas.entity.ApplicantSnapshot;
import hk.edu.sehs4701.cissas.entity.IdentityDocument;
import hk.edu.sehs4701.cissas.entity.Member;
import hk.edu.sehs4701.cissas.entity.ProgrammeLevel;
import hk.edu.sehs4701.cissas.enums.AccountStatus;
import hk.edu.sehs4701.cissas.enums.ApplicationStatus;
import hk.edu.sehs4701.cissas.enums.IdentityDocumentType;
import hk.edu.sehs4701.cissas.repository.ApplicantRepository;
import hk.edu.sehs4701.cissas.repository.ApplicationRepository;
import hk.edu.sehs4701.cissas.repository.MemberRepository;
import hk.edu.sehs4701.cissas.repository.ProgrammeLevelRepository;
import hk.edu.sehs4701.cissas.repository.IdentityReviewLockRepository;
import io.hypersistence.tsid.TSID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;


@Service
public class AdmissionsApplicationService {
    private final ApplicationRepository applicationRepository;
    private final ApplicantRepository applicantRepository;
    private final MemberRepository memberRepository;
    private final ProgrammeLevelRepository programmeLevelRepository;
    private final AcademicYearService academicYearService;
    private final IdentityDocumentService identityDocumentService;
    private final NotificationService notificationService;
    private final IdentityReviewLockRepository identityReviewLockRepository;
    private final IdentityReviewService identityReviewService;

    @Autowired
    public AdmissionsApplicationService(
            ApplicationRepository applicationRepository,
            ApplicantRepository applicantRepository,
            MemberRepository memberRepository,
            ProgrammeLevelRepository programmeLevelRepository,
            AcademicYearService academicYearService,
            IdentityDocumentService identityDocumentService,
            NotificationService notificationService,
            IdentityReviewLockRepository identityReviewLockRepository,
            IdentityReviewService identityReviewService) {
        this.applicationRepository = applicationRepository;
        this.applicantRepository = applicantRepository;
        this.memberRepository = memberRepository;
        this.programmeLevelRepository = programmeLevelRepository;
        this.academicYearService = academicYearService;
        this.identityDocumentService = identityDocumentService;
        this.notificationService = notificationService;
        this.identityReviewLockRepository = identityReviewLockRepository;
        this.identityReviewService = identityReviewService;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, isolation = Isolation.READ_COMMITTED)
    public void submitApplication(ApplicationSubmissionRequest applicationSubmissionRequest, Long memberId) {
        identityReviewLockRepository.acquire();

        Member member = memberRepository.findByIdForUpdate(memberId)
                .orElseThrow(() -> new IllegalArgumentException("Member not found"));
        if (member.getAccountStatus() != AccountStatus.ACTIVE) {
            throw new IllegalStateException("Member account is not activated.");
        }

        AcademicYearResponse openAcademicYear = academicYearService.getActiveAcademicYear();
        ProgrammeLevel level = programmeLevelRepository
                .findByLevelCodeAndAcademicYear_Id(applicationSubmissionRequest.levelCode(), openAcademicYear.id())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.CONFLICT, "Programme level is not in the currently open academic year."));

        LocalDate today = LocalDate.now(ZoneId.of("Asia/Hong_Kong"));
        if (today.isBefore(openAcademicYear.applicationStartDate())
                || today.isAfter(openAcademicYear.applicationEndDate())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Application period is closed.");
        }

        if (LocalDateTime.now(ZoneId.of("Asia/Hong_Kong")).isAfter(level.getApplicationDeadline())) {
            throw new IllegalStateException("Application deadline for " + level.getLevelCode() + " has passed.");
        }

        String documentNumberHash = identityDocumentService.getDocumentNumberHash(
                applicationSubmissionRequest.applicantIdentityDocumentType(),
                applicationSubmissionRequest.applicantIdentityDocumentNumber(),
                applicationSubmissionRequest.applicantIdentityDocumentIssuingCountry()
        );
        Applicant applicant = applicantRepository.findByIdentityDocumentHashForUpdate(documentNumberHash)
                .orElse(null);

        Set<Long> confirmedApplicantIds = Set.of();

        if (applicant != null) {
            if (!Objects.equals(applicant.getMember().getId(), member.getId())) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Identity document is already linked");
            }
            applicationRepository
                    .findFirstByApplicant_IdentityDocuments_DocumentNumberHashAndProgrammeLevel_IdAndAcademicYear_IdAndStatusNotOrderBySubmittedAtDesc(
                            documentNumberHash,
                            level.getId(),
                            openAcademicYear.id(),
                            ApplicationStatus.Cancelled
                    )
                    .ifPresent(existing -> {
                        throw new ResponseStatusException(
                                HttpStatus.CONFLICT,
                                "An application already exists. Application number: "
                                        + existing.getApplicationNumber()
                        );
                    });

            Long applicantId = Objects.requireNonNull(applicant.getId(), "Existing applicant must have an ID");
            confirmedApplicantIds = identityReviewService.confirmedApplicantIds(applicantId);

            var relatedIds = new HashSet<>(confirmedApplicantIds);
            relatedIds.remove(applicantId);
            if (!relatedIds.isEmpty()) {
                applicationRepository
                        .findFirstByApplicant_IdInAndProgrammeLevel_IdAndAcademicYear_IdAndStatusNotOrderBySubmittedAtDesc(
                                relatedIds, level.getId(), openAcademicYear.id(), ApplicationStatus.Cancelled)
                        .ifPresent(existing -> {
                            throw new ResponseStatusException(HttpStatus.CONFLICT,
                                    "An application already exists for a confirmed applicant.");
                        });
            }
        }

        boolean manualIdentityReviewRequired = hasCrossDocumentMatch(
                applicationSubmissionRequest, level, openAcademicYear.id(), applicant, confirmedApplicantIds);

        if (applicant == null) {
            IdentityDocument identityDocument = identityDocumentService.findByHashForUpdate(documentNumberHash)
                    .orElse(null);
            if (identityDocument != null && (identityDocument.getMember() == null
                    || !Objects.equals(identityDocument.getMember().getId(), member.getId())
                    || identityDocument.getApplicant() != null)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Identity document is already linked");
            }
            applicant = new Applicant();
            applicant.setMember(member);
            if (identityDocument == null) {
                identityDocument = identityDocumentService.createIdentityDocument(
                        applicationSubmissionRequest.applicantIdentityDocumentType(),
                        applicationSubmissionRequest.applicantIdentityDocumentNumber(),
                        applicationSubmissionRequest.applicantIdentityDocumentIssuingCountry()
                );
            }
            applicant.addIdentityDocument(identityDocument);
            applicant = applicantRepository.save(applicant);
        }

        ApplicantSnapshot snapshot = new ApplicantSnapshot(
                applicationSubmissionRequest.applicantFullName().trim(),
                applicationSubmissionRequest.dateOfBirth(),
                applicationSubmissionRequest.gender().trim(),
                applicationSubmissionRequest.phone().trim(),
                applicationSubmissionRequest.previousSchool().trim(),
                applicationSubmissionRequest.applicantIdentityDocumentType(),
                applicationSubmissionRequest.applicantIdentityDocumentNumber(),
                applicationSubmissionRequest.applicantIdentityDocumentIssuingCountry() == null
                        ? null : applicationSubmissionRequest.applicantIdentityDocumentIssuingCountry().trim()
        );

        Application application = new Application();
        application.setApplicationNumber("APP-" + TSID.Factory.getTsid());
        application.setMember(member);
        application.setApplicant(applicant);
        application.setApplicantSnapshot(snapshot);
        application.setProgrammeLevel(level);
        application.setAcademicYear(level.getAcademicYear());
        application.setStatus(ApplicationStatus.Pending);
        application.setManualIdentityReviewRequired(manualIdentityReviewRequired);
        Application savedApplication = applicationRepository.save(application);
        if (manualIdentityReviewRequired) {
            notificationService.notifyAdminOfIdentityReview(
                    member, savedApplication, openAcademicYear.name());
        }
    }

    private boolean hasCrossDocumentMatch(
            ApplicationSubmissionRequest request,
            ProgrammeLevel level,
            Long academicYearId,
            Applicant applicant,
            Set<Long> confirmedApplicantIds) {
        IdentityDocumentType submittedType = request.applicantIdentityDocumentType();
        IdentityDocumentType oppositeType;
        if (submittedType == IdentityDocumentType.HKID) {
            oppositeType = IdentityDocumentType.PASSPORT;
        } else if (submittedType == IdentityDocumentType.PASSPORT) {
            oppositeType = IdentityDocumentType.HKID;
        } else {
            return false;
        }

        String normalizedName = ApplicantSnapshot.normalizeName(request.applicantFullName());
        return applicationRepository
                .findAllByProgrammeLevel_IdAndAcademicYear_IdAndStatusNotAndApplicantSnapshot_DateOfBirthAndApplicantSnapshot_DocumentTypeAndApplicantSnapshot_NameHash(
                        level.getId(), academicYearId, ApplicationStatus.Cancelled, request.dateOfBirth(), oppositeType,
                        ApplicantSnapshot.hashName(request.applicantFullName()))
                .stream()
                .filter(application -> ApplicantSnapshot.normalizeName(application.getApplicantSnapshot().getFullName())
                        .equals(normalizedName))
                .anyMatch(application -> {
                    if (applicant == null) return true;
                    Long candidateId = application.getApplicant().getId();
                    return !confirmedApplicantIds.contains(candidateId)
                            && !identityReviewService.hasResolvedMatch(confirmedApplicantIds, candidateId);
                });
    }
}
