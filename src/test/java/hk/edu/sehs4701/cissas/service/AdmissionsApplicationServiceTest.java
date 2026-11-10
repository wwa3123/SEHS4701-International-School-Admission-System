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
import java.util.List;
import java.util.Optional;
import org.mockito.ArgumentCaptor;

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
    private final IdentityReviewLockRepository identityReviewLockRepository = mock(IdentityReviewLockRepository.class);
    private final IdentityReviewService identityReviewService = mock(IdentityReviewService.class);
    private final AdmissionsApplicationService service = new AdmissionsApplicationService(
            applicationRepository,
            applicantRepository,
            memberRepository,
            programmeLevelRepository,
            academicYearService,
            identityDocumentService,
            notificationService,
            identityReviewLockRepository,
            identityReviewService
    );

    private Member member;
    private ProgrammeLevel programmeLevel;
    private ApplicationSubmissionRequest request;

    @BeforeEach
    void setUp() {
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Hong_Kong"));
        AcademicYearResponse openYear = new AcademicYearResponse(
                ACADEMIC_YEAR_ID,
                "2026/27",
                today.minusDays(10),
                null,
                AcademicYearStatus.ACTIVE
        );
        when(academicYearService.getActiveAcademicYear()).thenReturn(openYear);

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
        programmeLevel.setApplicationStartDate(today.minusDays(10));
        programmeLevel.setApplicationEndDate(today.plusDays(10));
        when(programmeLevelRepository.findByLevelCodeAndAcademicYear_Id(LevelCode.S1, ACADEMIC_YEAR_ID))
                .thenReturn(Optional.of(programmeLevel));
        when(identityDocumentService.getDocumentNumberHash(
                IdentityDocumentType.HKID, "A123456(3)", null
        )).thenReturn(DOCUMENT_HASH);

        request = new ApplicationSubmissionRequest(
                LevelCode.S1,
                "Applicant Name",
                "A123456(3)",
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
        Applicant applicant = new Applicant();
        applicant.setMember(member);
        when(applicantRepository.findByIdentityDocumentHashForUpdate(DOCUMENT_HASH))
                .thenReturn(Optional.of(applicant));
        Application existingApplication = new Application();
        existingApplication.setApplicationNumber("APP-EXISTING123");
        when(applicationRepository
                .findFirstByApplicant_IdentityDocuments_DocumentNumberHashAndProgrammeLevel_IdAndAcademicYear_IdAndStatusNotOrderBySubmittedAtDesc(
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
    void flagsApplicationForPreviouslyConfirmedApplicantRecords() {
        when(identityReviewService.lacksDifferentDecision(2L, 1L)).thenReturn(true);
        Applicant applicant = new Applicant();
        applicant.setId(1L);
        applicant.setMember(member);
        when(applicantRepository.findByIdentityDocumentHashForUpdate(DOCUMENT_HASH)).thenReturn(Optional.of(applicant));
        Applicant linkedApplicant = new Applicant();
        linkedApplicant.setId(2L);
        Application existing = new Application();
        existing.setApplicant(linkedApplicant);
        existing.setApplicantSnapshot(new ApplicantSnapshot(
                "Applicant Name", LocalDate.of(2018, 1, 1), "Unspecified",
                "12345678", "Previous School", IdentityDocumentType.PASSPORT, "P123456", "HK"));
        when(applicationRepository.findAllByStatusNotAndApplicantSnapshot_DateOfBirthAndApplicantSnapshot_NameHash(
                eq(ApplicationStatus.Cancelled), eq(LocalDate.of(2018, 1, 1)), any(byte[].class)))
                .thenReturn(List.of(existing));

        service.submitApplication(request, MEMBER_ID);

        verify(applicationRepository).save(argThat(application ->
                application.isManualIdentityReviewRequired() && application.getStatus() == ApplicationStatus.Pending));
    }

    @Test
    void blocksSubmissionBeforeProgrammeApplicationStartDate() {
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Hong_Kong"));
        programmeLevel.setApplicationStartDate(today.plusDays(1));

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class, () -> service.submitApplication(request, MEMBER_ID)
        );

        assertEquals(HttpStatus.CONFLICT, exception.getStatusCode());
        verifyNoInteractions(identityDocumentService);
    }

    @Test
    void rejectsLevelAbsentFromActiveYear() {
        when(programmeLevelRepository.findByLevelCodeAndAcademicYear_Id(LevelCode.S1, ACADEMIC_YEAR_ID))
                .thenReturn(Optional.empty());
        assertEquals(HttpStatus.CONFLICT, assertThrows(ResponseStatusException.class,
                () -> service.submitApplication(request, MEMBER_ID)).getStatusCode());
        verifyNoInteractions(identityDocumentService);
    }

    @Test
    void blocksSubmissionAfterProgrammeApplicationEndDate() {
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Hong_Kong"));
        programmeLevel.setApplicationEndDate(today.minusDays(1));

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class, () -> service.submitApplication(request, MEMBER_ID)
        );

        assertEquals(HttpStatus.CONFLICT, exception.getStatusCode());
        verifyNoInteractions(identityDocumentService);
    }

    @Test
    void acceptsSubmissionDuringTheProgrammeApplicationPeriod() {
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Hong_Kong"));
        programmeLevel.setApplicationStartDate(today);
        programmeLevel.setApplicationEndDate(today.plusDays(1));
        when(applicantRepository.findByIdentityDocumentHashForUpdate(DOCUMENT_HASH))
                .thenReturn(Optional.empty());
        IdentityDocument identityDocument = new IdentityDocument(
                IdentityDocumentType.HKID, "A123456(3)", "HK", DOCUMENT_HASH);
        when(identityDocumentService.createIdentityDocument(
                IdentityDocumentType.HKID, "A123456(3)", null
        )).thenReturn(identityDocument);
        when(applicantRepository.save(any(Applicant.class))).thenAnswer(invocation -> invocation.getArgument(0));

        assertDoesNotThrow(() -> service.submitApplication(request, MEMBER_ID));

        verify(applicationRepository).save(any(Application.class));
    }

    @Test
    void flagsNameAndBirthMatchesRegardlessOfDocumentProgrammeOrYear() {
        when(applicantRepository.findByIdentityDocumentHashForUpdate(DOCUMENT_HASH)).thenReturn(Optional.empty());
        IdentityDocument identityDocument = new IdentityDocument(
                IdentityDocumentType.HKID, "A123456(3)", "HK", DOCUMENT_HASH);
        when(identityDocumentService.createIdentityDocument(
                IdentityDocumentType.HKID, "A123456(3)", null)).thenReturn(identityDocument);
        when(applicantRepository.save(any(Applicant.class))).thenAnswer(invocation -> {
            Applicant saved = invocation.getArgument(0);
            saved.setId(99L);
            return saved;
        });
        Applicant otherApplicant = new Applicant();
        otherApplicant.setId(98L);
        Application otherApplication = new Application();
        otherApplication.setApplicant(otherApplicant);
        otherApplication.setApplicantSnapshot(new ApplicantSnapshot(
                "  APPLICANT   NAME ", LocalDate.of(2018, 1, 1), "Unspecified",
                "87654321", "Another School", IdentityDocumentType.HKID, "B123456(3)", null));
        when(applicationRepository.findAllByStatusNotAndApplicantSnapshot_DateOfBirthAndApplicantSnapshot_NameHash(
                eq(ApplicationStatus.Cancelled), eq(LocalDate.of(2018, 1, 1)), any(byte[].class)))
                .thenReturn(List.of(otherApplication));

        service.submitApplication(request, MEMBER_ID);

        verify(applicationRepository).save(argThat(application -> application.getStatus() == ApplicationStatus.Pending
                && application.isManualIdentityReviewRequired()));
        verify(notificationService).notifyAdminOfPossibleDuplicate(any(), any(), eq("2026/27"));
    }

    @Test
    void allowsSubmissionWhenPreviousApplicationIsCancelled() {
        Applicant applicant = new Applicant();
        applicant.setId(1L);
        applicant.setMember(member);
        when(applicantRepository.findByIdentityDocumentHashForUpdate(DOCUMENT_HASH))
                .thenReturn(Optional.of(applicant));
        when(applicationRepository
                .findFirstByApplicant_IdentityDocuments_DocumentNumberHashAndProgrammeLevel_IdAndAcademicYear_IdAndStatusNotOrderBySubmittedAtDesc(
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
                        && application.getApplicantSnapshot().getFullName().equals("Applicant Name")
        ));
    }

    @Test
    void resubmissionKeepsCancelledApplicationSnapshotAndSavesNewDetails() {
        Applicant applicant = new Applicant();
        applicant.setId(1L);
        applicant.setMember(member);
        Application cancelledApplication = new Application();
        cancelledApplication.setStatus(ApplicationStatus.Cancelled);
        cancelledApplication.setApplicant(applicant);
        cancelledApplication.setApplicantSnapshot(new ApplicantSnapshot(
                "Old Name", LocalDate.of(2017, 1, 1), "Old Gender", "11111111", "Old School",
                IdentityDocumentType.HKID, "A123456(3)", null
        ));
        when(applicantRepository.findByIdentityDocumentHashForUpdate(DOCUMENT_HASH))
                .thenReturn(Optional.of(applicant));
        service.submitApplication(request, MEMBER_ID);

        ArgumentCaptor<Application> captor = ArgumentCaptor.forClass(Application.class);
        verify(applicationRepository).save(captor.capture());
        ApplicantSnapshot snapshot = captor.getValue().getApplicantSnapshot();
        assertSame(applicant, captor.getValue().getApplicant());
        verify(applicantRepository, never()).save(any());
        assertEquals("Applicant Name", snapshot.getFullName());
        assertEquals(LocalDate.of(2018, 1, 1), snapshot.getDateOfBirth());
        assertEquals("Unspecified", snapshot.getGender());
        assertEquals("12345678", snapshot.getPhone());
        assertEquals("Previous School", snapshot.getPreviousSchool());
        assertEquals(IdentityDocumentType.HKID, snapshot.getDocumentType());
        assertEquals("A123456(3)", snapshot.getDocumentNumber());
        assertNull(snapshot.getIssuingCountry());
        assertEquals("Old Name", cancelledApplication.getApplicantSnapshot().getFullName());
        assertEquals(LocalDate.of(2017, 1, 1), cancelledApplication.getApplicantSnapshot().getDateOfBirth());
        assertEquals("Old Gender", cancelledApplication.getApplicantSnapshot().getGender());
        assertEquals("11111111", cancelledApplication.getApplicantSnapshot().getPhone());
        assertEquals("Old School", cancelledApplication.getApplicantSnapshot().getPreviousSchool());
        assertEquals("A123456(3)", cancelledApplication.getApplicantSnapshot().getDocumentNumber());
        assertThrows(IllegalStateException.class, () -> cancelledApplication.setApplicantSnapshot(snapshot));
    }

    @Test
    void rejectsResubmissionByAnotherMember() {
        Member owner = new Member();
        owner.setId(99L);
        Applicant applicant = new Applicant();
        applicant.setId(1L);
        applicant.setMember(owner);
        when(applicantRepository.findByIdentityDocumentHashForUpdate(DOCUMENT_HASH))
                .thenReturn(Optional.of(applicant));

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class, () -> service.submitApplication(request, MEMBER_ID)
        );

        assertEquals(HttpStatus.CONFLICT, exception.getStatusCode());
        assertEquals("Identity document is already linked", exception.getReason());
        verifyNoInteractions(applicationRepository, identityReviewService);
        verify(applicantRepository, never()).save(any());
        verify(applicationRepository, never()).save(any());
    }

    @Test
    void duplicateSubmittedByAnotherMemberDoesNotRevealApplication() {
        Member owner = new Member();
        owner.setId(99L);
        Applicant applicant = new Applicant();
        applicant.setMember(owner);
        when(applicantRepository.findByIdentityDocumentHashForUpdate(DOCUMENT_HASH))
                .thenReturn(Optional.of(applicant));
        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class, () -> service.submitApplication(request, MEMBER_ID)
        );

        assertEquals(HttpStatus.CONFLICT, exception.getStatusCode());
        assertEquals("Identity document is already linked", exception.getReason());
        verifyNoInteractions(applicationRepository, identityReviewService);
        verify(applicantRepository, never()).save(any());
    }

    @Test
    void createsApplicantAndApplicationForFirstSubmission() {
        when(applicantRepository.findByIdentityDocumentHashForUpdate(DOCUMENT_HASH))
                .thenReturn(Optional.empty());
        IdentityDocument identityDocument = new IdentityDocument(
                IdentityDocumentType.HKID, "A123456(3)", "HK", DOCUMENT_HASH);
        when(identityDocumentService.createIdentityDocument(
                IdentityDocumentType.HKID, "A123456(3)", null
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
                        && !application.isManualIdentityReviewRequired()
                        && application.getApplicantSnapshot().getFullName().equals("Applicant Name")
                        && application.getApplicantSnapshot().getDocumentNumber().equals("A123456(3)")
        ));
    }

    @Test
    void memberCanSubmitUsingTheirOwnIdentityDocument() {
        IdentityDocument document = new IdentityDocument(
                IdentityDocumentType.HKID, "A123456(3)", "HK", DOCUMENT_HASH);
        member.addIdentityDocument(document);
        when(identityDocumentService.findByHashForUpdate(DOCUMENT_HASH)).thenReturn(Optional.of(document));
        when(applicantRepository.save(any(Applicant.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.submitApplication(request, MEMBER_ID);

        assertSame(member, document.getMember());
        assertNotNull(document.getApplicant());
        assertSame(member, document.getApplicant().getMember());
        verify(identityDocumentService, never()).createIdentityDocument(any(), any(), any());
        verify(applicationRepository).save(any(Application.class));
    }

    @Test
    void cannotSubmitUsingAnotherMembersIdentityDocument() {
        Member anotherMember = new Member();
        anotherMember.setId(99L);
        IdentityDocument document = new IdentityDocument(
                IdentityDocumentType.HKID, "A123456(3)", "HK", DOCUMENT_HASH);
        anotherMember.addIdentityDocument(document);
        when(identityDocumentService.findByHashForUpdate(DOCUMENT_HASH)).thenReturn(Optional.of(document));

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class, () -> service.submitApplication(request, MEMBER_ID));

        assertEquals(HttpStatus.CONFLICT, exception.getStatusCode());
        assertNull(document.getApplicant());
        verify(applicantRepository, never()).save(any());
        verify(applicationRepository, never()).save(any());
    }

    @Test
    void snapshotKeepsSubmittedDocumentFormatting() {
        request = new ApplicationSubmissionRequest(
                LevelCode.S1, "Applicant Name", " a123456(3) ", IdentityDocumentType.HKID, null,
                LocalDate.of(2018, 1, 1), "Unspecified", "12345678", "Previous School");
        when(identityDocumentService.getDocumentNumberHash(IdentityDocumentType.HKID, " a123456(3) ", null))
                .thenReturn(DOCUMENT_HASH);
        IdentityDocument document = new IdentityDocument(
                IdentityDocumentType.HKID, "A123456(3)", "HK", DOCUMENT_HASH);
        member.addIdentityDocument(document);
        when(identityDocumentService.findByHashForUpdate(DOCUMENT_HASH)).thenReturn(Optional.of(document));
        when(applicantRepository.save(any(Applicant.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.submitApplication(request, MEMBER_ID);

        ArgumentCaptor<Application> captor = ArgumentCaptor.forClass(Application.class);
        verify(applicationRepository).save(captor.capture());
        assertEquals(" a123456(3) ", captor.getValue().getApplicantSnapshot().getDocumentNumber());
        assertNull(captor.getValue().getApplicantSnapshot().getIssuingCountry());
        assertEquals("A123456(3)", document.getDocumentNumber());
        assertEquals("HK", document.getIssuingCountry());
    }

    @Test
    void duplicateLookupCanTraverseApplicantDocumentAndAcademicYear() {
        assertDoesNotThrow(() -> new PartTree(
                "findFirstByApplicant_IdentityDocuments_DocumentNumberHash"
                        + "AndProgrammeLevel_IdAndAcademicYear_Id"
                        + "AndStatusNotOrderBySubmittedAtDesc",
                Application.class
        ));
        assertDoesNotThrow(() -> new PartTree(
                "findAllByStatusNotAndApplicantSnapshot_DateOfBirthAndApplicantSnapshot_NameHash",
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
        String submittedNumber = submittedDocumentType == IdentityDocumentType.PASSPORT ? "P123456" : "A123456(3)";
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

        Application existingApplication = new Application();
        existingApplication.setApplicantSnapshot(new ApplicantSnapshot(
                "Applicant Name", LocalDate.of(2018, 1, 1), "Unspecified", "12345678",
                "Previous School", existingDocumentType, "old-document", "HK"
        ));
        when(applicationRepository.findAllByStatusNotAndApplicantSnapshot_DateOfBirthAndApplicantSnapshot_NameHash(
                ApplicationStatus.Cancelled, LocalDate.of(2018, 1, 1),
                ApplicantSnapshot.hashName(request.applicantFullName())))
                .thenReturn(List.of(existingApplication));
        IdentityDocument newDocument = new IdentityDocument(
                submittedDocumentType, submittedNumber, "HK", submittedHash);
        when(identityDocumentService.createIdentityDocument(
                submittedDocumentType, submittedNumber, "HK"
        )).thenReturn(newDocument);
        when(applicantRepository.save(any(Applicant.class))).thenAnswer(invocation -> invocation.getArgument(0));
        Application savedApplication = new Application();
        when(applicationRepository.save(any(Application.class))).thenReturn(savedApplication);

        service.submitApplication(request, MEMBER_ID);

        verify(applicationRepository).save(argThat(application -> application.isManualIdentityReviewRequired()
                && application.getApplicantSnapshot().getDocumentType() == submittedDocumentType
                && application.getApplicantSnapshot().getDocumentNumber().equals(submittedNumber)
                && application.getApplicantSnapshot().getIssuingCountry().equals("HK")));
        verify(notificationService).notifyAdminOfPossibleDuplicate(
                eq(member), same(savedApplication), eq("2026/27")
        );
    }
}
