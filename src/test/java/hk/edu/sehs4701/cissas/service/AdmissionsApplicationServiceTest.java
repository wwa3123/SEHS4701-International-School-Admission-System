package hk.edu.sehs4701.cissas.service;

import hk.edu.sehs4701.cissas.dto.request.ApplicationSubmissionRequest;
import hk.edu.sehs4701.cissas.dto.response.AcademicYearResponse;
import hk.edu.sehs4701.cissas.entity.*;
import hk.edu.sehs4701.cissas.enums.*;
import hk.edu.sehs4701.cissas.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.repository.query.parser.PartTree;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AdmissionsApplicationServiceTest {
    private static final Long ACADEMIC_YEAR_ID = 10L;
    private static final Long PROGRAMME_LEVEL_ID = 20L;
    private static final Long MEMBER_ID = 30L;
    private static final String DOCUMENT_HASH = "document-hash";

    private final ApplicationRepository applicationRepository = mock(ApplicationRepository.class);
    private final ApplicantRepository applicantRepository = mock(ApplicantRepository.class);
    private final MemberRepository memberRepository = mock(MemberRepository.class);
    private final ProgrammeLevelRepository programmeLevelRepository = mock(ProgrammeLevelRepository.class);
    private final AcademicYearService academicYearService = mock(AcademicYearService.class);
    private final IdentityDocumentService identityDocumentService = mock(IdentityDocumentService.class);
    private final NotificationService notificationService = mock(NotificationService.class);
    private final AdmissionsApplicationService service = new AdmissionsApplicationService(
            applicationRepository,
            applicantRepository,
            memberRepository,
            programmeLevelRepository,
            academicYearService,
            identityDocumentService,
            notificationService
    );

    private Member member;
    private ProgrammeLevel programmeLevel;
    private ApplicationSubmissionRequest request;

    @BeforeEach
    void setUp() {
        AcademicYearResponse openYear = new AcademicYearResponse(
                ACADEMIC_YEAR_ID,
                "2026/27",
                LocalDate.of(2026, 9, 1),
                LocalDate.of(2027, 8, 31),
                AcademicYearStatus.OPEN_FOR_ADMISSION
        );
        when(academicYearService.getCurrentOpenForApplicationAcademicYear()).thenReturn(openYear);

        member = new Member();
        member.setId(MEMBER_ID);
        member.setAccountStatus(AccountStatus.ACTIVE);
        when(memberRepository.findByIdForUpdate(MEMBER_ID)).thenReturn(Optional.of(member));

        AcademicYear academicYear = new AcademicYear();
        academicYear.setId(ACADEMIC_YEAR_ID);
        programmeLevel = new ProgrammeLevel();
        programmeLevel.setId(PROGRAMME_LEVEL_ID);
        programmeLevel.setLevelCode(LevelCode.S1);
        programmeLevel.setAcademicYear(academicYear);
        programmeLevel.setApplicationDeadline(LocalDateTime.now(ZoneId.of("Asia/Hong_Kong")).plusDays(1));
        when(programmeLevelRepository.findByLevelCode(LevelCode.S1)).thenReturn(Optional.of(programmeLevel));
        when(identityDocumentService.getDocumentNumberHash(
                IdentityDocumentType.HKID, "A123456(8)", null
        )).thenReturn(DOCUMENT_HASH);

        request = new ApplicationSubmissionRequest(
                LevelCode.S1,
                "Applicant Name",
                "A123456(8)",
                IdentityDocumentType.HKID,
                null,
                LocalDate.of(2018, 1, 1),
                "Unspecified",
                "12345678",
                "Previous School"
        );
    }

    @Test
    void blocksDuplicateAndReturnsExistingApplicationNumber() {
        when(applicantRepository.findByIdentityDocumentHashForUpdate(DOCUMENT_HASH))
                .thenReturn(Optional.of(new Applicant()));
        Application existingApplication = new Application();
        existingApplication.setApplicationNumber("APP-EXISTING123");
        when(applicationRepository
                .findFirstByApplicant_IdentityDocuments_DocumentNumberHashAndProgrammeLevel_IdAndProgrammeLevel_AcademicYear_IdAndStatusNotOrderBySubmittedAtDesc(
                        DOCUMENT_HASH, PROGRAMME_LEVEL_ID, ACADEMIC_YEAR_ID, ApplicationStatus.Cancelled
                ))
                .thenReturn(Optional.of(existingApplication));

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class, () -> service.submitApplication(request, MEMBER_ID)
        );

        assertEquals(HttpStatus.CONFLICT, exception.getStatusCode());
        assertTrue(exception.getReason().contains("APP-EXISTING123"));
        verify(applicationRepository, never()).save(any(Application.class));
    }

    @Test
    void blocksSubmissionBeforeAcademicYearApplicationStartDate() {
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Hong_Kong"));
        when(academicYearService.getCurrentOpenForApplicationAcademicYear()).thenReturn(
                new AcademicYearResponse(
                        ACADEMIC_YEAR_ID, "2026/27", today.plusDays(1), today.plusDays(10),
                        AcademicYearStatus.OPEN_FOR_ADMISSION
                )
        );

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class, () -> service.submitApplication(request, MEMBER_ID)
        );

        assertEquals(HttpStatus.CONFLICT, exception.getStatusCode());
        verifyNoInteractions(identityDocumentService);
    }

    @Test
    void blocksSubmissionAfterAcademicYearApplicationEndDate() {
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Hong_Kong"));
        when(academicYearService.getCurrentOpenForApplicationAcademicYear()).thenReturn(
                new AcademicYearResponse(
                        ACADEMIC_YEAR_ID, "2026/27", today.minusDays(10), today.minusDays(1),
                        AcademicYearStatus.OPEN_FOR_ADMISSION
                )
        );

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class, () -> service.submitApplication(request, MEMBER_ID)
        );

        assertEquals(HttpStatus.CONFLICT, exception.getStatusCode());
        verifyNoInteractions(identityDocumentService);
    }

    @Test
    void acceptsSubmissionOnAcademicYearStartAndEndDates() {
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Hong_Kong"));
        when(academicYearService.getCurrentOpenForApplicationAcademicYear()).thenReturn(
                new AcademicYearResponse(
                        ACADEMIC_YEAR_ID, "2026/27", today, today,
                        AcademicYearStatus.OPEN_FOR_ADMISSION
                )
        );
        when(applicantRepository.findByIdentityDocumentHashForUpdate(DOCUMENT_HASH))
                .thenReturn(Optional.empty());
        IdentityDocument identityDocument = new IdentityDocument();
        when(identityDocumentService.createIdentityDocument(
                IdentityDocumentType.HKID, "A123456(8)", null
        )).thenReturn(identityDocument);
        when(applicantRepository.save(any(Applicant.class))).thenAnswer(invocation -> invocation.getArgument(0));

        assertDoesNotThrow(() -> service.submitApplication(request, MEMBER_ID));

        verify(applicationRepository).save(any(Application.class));
    }

    @Test
    void allowsSubmissionWhenPreviousApplicationIsCancelled() {
        Applicant applicant = new Applicant();
        when(applicantRepository.findByIdentityDocumentHashForUpdate(DOCUMENT_HASH))
                .thenReturn(Optional.of(applicant));
        when(applicationRepository
                .findFirstByApplicant_IdentityDocuments_DocumentNumberHashAndProgrammeLevel_IdAndProgrammeLevel_AcademicYear_IdAndStatusNotOrderBySubmittedAtDesc(
                        DOCUMENT_HASH, PROGRAMME_LEVEL_ID, ACADEMIC_YEAR_ID, ApplicationStatus.Cancelled
                ))
                .thenReturn(Optional.empty());

        service.submitApplication(request, MEMBER_ID);

        verify(applicationRepository).save(argThat(application ->
                application.getApplicationNumber().startsWith("APP-")
                        && application.getApplicationNumber().length() == 17
                        && application.getApplicant() == applicant
                        && application.getProgrammeLevel() == programmeLevel
                        && application.getMember() == member
        ));
    }

    @Test
    void createsApplicantAndApplicationForFirstSubmission() {
        when(applicantRepository.findByIdentityDocumentHashForUpdate(DOCUMENT_HASH))
                .thenReturn(Optional.empty());
        IdentityDocument identityDocument = new IdentityDocument();
        identityDocument.setDocumentNumberHash(DOCUMENT_HASH);
        when(identityDocumentService.createIdentityDocument(
                IdentityDocumentType.HKID, "A123456(8)", null
        )).thenReturn(identityDocument);
        when(applicantRepository.save(any(Applicant.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.submitApplication(request, MEMBER_ID);

        verify(applicantRepository).save(argThat(applicant ->
                applicant.getMember() == member
                        && applicant.getIdentityDocuments().size() == 1
                        && applicant.getIdentityDocuments().getFirst() == identityDocument
        ));
        verify(applicationRepository).save(argThat(application ->
                application.getApplicationNumber().matches("APP-[0-9A-Z]{13}")
                        && application.getStatus() == ApplicationStatus.Pending
        ));
    }

    @Test
    void duplicateLookupCanTraverseApplicantDocumentAndAcademicYear() {
        assertDoesNotThrow(() -> new PartTree(
                "findFirstByApplicant_IdentityDocuments_DocumentNumberHash"
                        + "AndProgrammeLevel_IdAndProgrammeLevel_AcademicYear_Id"
                        + "AndStatusNotOrderBySubmittedAtDesc",
                Application.class
        ));
        assertDoesNotThrow(() -> new PartTree(
                "existsByApplicant_IdInAndProgrammeLevel_IdAndProgrammeLevel_AcademicYear_IdAndStatusNot",
                Application.class
        ));
    }

    @Test
    void flagsPassportSubmissionMatchingExistingHkidApplication() {
        assertManualReviewForCrossDocument(IdentityDocumentType.PASSPORT, IdentityDocumentType.HKID);
    }

    @Test
    void flagsHkidSubmissionMatchingExistingPassportApplication() {
        assertManualReviewForCrossDocument(IdentityDocumentType.HKID, IdentityDocumentType.PASSPORT);
    }

    private void assertManualReviewForCrossDocument(
            IdentityDocumentType submittedDocumentType,
            IdentityDocumentType existingDocumentType) {
        String submittedNumber = submittedDocumentType == IdentityDocumentType.PASSPORT ? "P123456" : "A123456(8)";
        String submittedHash = "submitted-" + submittedDocumentType;
        request = new ApplicationSubmissionRequest(
                LevelCode.S1,
                "  APPLICANT   NAME ",
                submittedNumber,
                submittedDocumentType,
                "HK",
                LocalDate.of(2018, 1, 1),
                "Unspecified",
                "12345678",
                "Previous School"
        );
        when(identityDocumentService.getDocumentNumberHash(
                submittedDocumentType, submittedNumber, "HK"
        )).thenReturn(submittedHash);
        when(applicantRepository.findByIdentityDocumentHashForUpdate(submittedHash))
                .thenReturn(Optional.empty());

        Applicant existingApplicant = new Applicant();
        existingApplicant.setId(99L);
        existingApplicant.setFullName("Applicant Name");
        existingApplicant.setDateOfBirth(LocalDate.of(2018, 1, 1));
        IdentityDocument existingDocument = new IdentityDocument();
        existingDocument.setDocumentType(existingDocumentType);
        existingApplicant.addIdentityDocument(existingDocument);
        when(applicantRepository.findAllByDateOfBirth(LocalDate.of(2018, 1, 1)))
                .thenReturn(java.util.List.of(existingApplicant));
        when(applicationRepository
                .existsByApplicant_IdInAndProgrammeLevel_IdAndProgrammeLevel_AcademicYear_IdAndStatusNot(
                        java.util.List.of(99L), PROGRAMME_LEVEL_ID, ACADEMIC_YEAR_ID, ApplicationStatus.Cancelled
                ))
                .thenReturn(true);
        IdentityDocument newDocument = new IdentityDocument();
        newDocument.setDocumentType(submittedDocumentType);
        newDocument.setDocumentNumberHash(submittedHash);
        when(identityDocumentService.createIdentityDocument(
                submittedDocumentType, submittedNumber, "HK"
        )).thenReturn(newDocument);
        when(applicantRepository.save(any(Applicant.class))).thenAnswer(invocation -> invocation.getArgument(0));
        Application savedApplication = new Application();
        when(applicationRepository.save(any(Application.class))).thenReturn(savedApplication);

        service.submitApplication(request, MEMBER_ID);

        verify(applicationRepository).save(argThat(application -> application.isManualIdentityReviewRequired()));
        verify(notificationService).notifyAdminOfIdentityReview(
                eq(member), any(Applicant.class), same(savedApplication), eq("2026/27")
        );
    }
}
