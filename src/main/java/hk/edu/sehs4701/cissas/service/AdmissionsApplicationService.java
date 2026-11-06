package hk.edu.sehs4701.cissas.service;

import hk.edu.sehs4701.cissas.dto.request.ApplicationSubmissionRequest;
import hk.edu.sehs4701.cissas.dto.response.AcademicYearResponse;
import hk.edu.sehs4701.cissas.entity.Application;
import hk.edu.sehs4701.cissas.entity.Applicant;
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
import io.hypersistence.tsid.TSID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Locale;
import java.util.Objects;


@Service
public class AdmissionsApplicationService {
    private final ApplicationRepository applicationRepository;
    private final ApplicantRepository applicantRepository;
    private final MemberRepository memberRepository;
    private final ProgrammeLevelRepository programmeLevelRepository;
    private final AcademicYearService academicYearService;
    private final IdentityDocumentService identityDocumentService;
    private final NotificationService notificationService;

    @Autowired
    public AdmissionsApplicationService(
            ApplicationRepository applicationRepository,
            ApplicantRepository applicantRepository,
            MemberRepository memberRepository,
            ProgrammeLevelRepository programmeLevelRepository,
            AcademicYearService academicYearService,
            IdentityDocumentService identityDocumentService,
            NotificationService notificationService) {
        this.applicationRepository = applicationRepository;
        this.applicantRepository = applicantRepository;
        this.memberRepository = memberRepository;
        this.programmeLevelRepository = programmeLevelRepository;
        this.academicYearService = academicYearService;
        this.identityDocumentService = identityDocumentService;
        this.notificationService = notificationService;
    }

    @Transactional
    public void submitApplication(ApplicationSubmissionRequest applicationSubmissionRequest, Long memberId) {
        AcademicYearResponse openAcademicYear = academicYearService.getCurrentOpenForApplicationAcademicYear();

        Member member = memberRepository.findByIdForUpdate(memberId)
                .orElseThrow(() -> new IllegalArgumentException("Member not found"));

        if (member.getAccountStatus() != AccountStatus.ACTIVE) {
            throw new IllegalStateException("Member account is not activated.");
        }

        ProgrammeLevel level = programmeLevelRepository.findByLevelCode(applicationSubmissionRequest.levelCode())
                .orElseThrow(() -> new IllegalArgumentException("Programme level not found."));

        if (level.getAcademicYear() == null
                || !Objects.equals(level.getAcademicYear().getId(), openAcademicYear.id())) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "Programme level is not in the currently open academic year.");
        }

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

        if (applicant != null) {
            applicationRepository
                    .findFirstByApplicant_IdentityDocuments_DocumentNumberHashAndProgrammeLevel_IdAndProgrammeLevel_AcademicYear_IdAndStatusNotOrderBySubmittedAtDesc(
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
        }

        boolean manualIdentityReviewRequired = hasCrossDocumentMatch(
                applicationSubmissionRequest, level, openAcademicYear.id());

        if (applicant == null) {
            applicant = new Applicant();
            applicant.setMember(member);
            applicant.setFullName(applicationSubmissionRequest.applicantFullName().trim());
            applicant.setDateOfBirth(applicationSubmissionRequest.dateOfBirth());
            applicant.setGender(applicationSubmissionRequest.gender().trim());
            applicant.setPhone(applicationSubmissionRequest.phone().trim());
            applicant.setPreviousSchool(applicationSubmissionRequest.previousSchool().trim());

            IdentityDocument identityDocument = identityDocumentService.createIdentityDocument(
                    applicationSubmissionRequest.applicantIdentityDocumentType(),
                    applicationSubmissionRequest.applicantIdentityDocumentNumber(),
                    applicationSubmissionRequest.applicantIdentityDocumentIssuingCountry()
            );
            applicant.addIdentityDocument(identityDocument);
            applicant = applicantRepository.save(applicant);
        }

        Application application = new Application();
        application.setApplicationNumber("APP-" + TSID.Factory.getTsid());
        application.setMember(member);
        application.setApplicant(applicant);
        application.setProgrammeLevel(level);
        application.setStatus(ApplicationStatus.Pending);
        application.setManualIdentityReviewRequired(manualIdentityReviewRequired);
        Application savedApplication = applicationRepository.save(application);
        if (manualIdentityReviewRequired) {
            notificationService.notifyAdminOfIdentityReview(
                    member, applicant, savedApplication, openAcademicYear.name());
        }
    }

    private boolean hasCrossDocumentMatch(
            ApplicationSubmissionRequest request,
            ProgrammeLevel level,
            Long academicYearId) {
        IdentityDocumentType submittedType = request.applicantIdentityDocumentType();
        var matchingApplicantIds = applicantRepository.findAllByDateOfBirth(request.dateOfBirth()).stream()
                .filter(applicant -> normalizeName(applicant.getFullName())
                        .equals(normalizeName(request.applicantFullName())))
                .filter(applicant -> hasOppositeDocumentType(applicant, submittedType))
                .map(Applicant::getId)
                .toList();
        return !matchingApplicantIds.isEmpty()
                && applicationRepository.existsByApplicant_IdInAndProgrammeLevel_IdAndProgrammeLevel_AcademicYear_IdAndStatusNot(
                        matchingApplicantIds,
                        level.getId(),
                        academicYearId,
                        ApplicationStatus.Cancelled
                );
    }

    private boolean hasOppositeDocumentType(Applicant applicant, IdentityDocumentType submittedType) {
        boolean submittedPassport = submittedType == IdentityDocumentType.PASSPORT;
        boolean submittedHkid = submittedType == IdentityDocumentType.HKID;
        return applicant.getIdentityDocuments().stream().anyMatch(document ->
                submittedPassport && document.getDocumentType() == IdentityDocumentType.HKID
                        || submittedHkid && document.getDocumentType() == IdentityDocumentType.PASSPORT
        );
    }

    private String normalizeName(String fullName) {
        return fullName.trim().replaceAll("\\s+", " ").toUpperCase(Locale.ROOT);
    }

}
