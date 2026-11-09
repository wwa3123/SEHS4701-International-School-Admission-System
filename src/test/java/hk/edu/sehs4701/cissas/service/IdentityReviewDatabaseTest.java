package hk.edu.sehs4701.cissas.service;

import hk.edu.sehs4701.cissas.dto.request.ApplicationSubmissionRequest;
import hk.edu.sehs4701.cissas.dto.request.AccountRequest;
import hk.edu.sehs4701.cissas.dto.request.CreateAcademicYearRequest;
import hk.edu.sehs4701.cissas.dto.request.PasswordChangeRequest;
import hk.edu.sehs4701.cissas.dto.request.PasswordResetRequest;
import hk.edu.sehs4701.cissas.dto.response.Candidate;
import hk.edu.sehs4701.cissas.dto.response.CandidatePage;
import hk.edu.sehs4701.cissas.dto.response.Decision;
import hk.edu.sehs4701.cissas.controller.api.member.AdmissionsApplicationController;
import hk.edu.sehs4701.cissas.controller.api.admin.IdentityReviewController;
import hk.edu.sehs4701.cissas.controller.api.admin.SuperAdminAcademicYearController;
import hk.edu.sehs4701.cissas.entity.*;
import hk.edu.sehs4701.cissas.enums.*;
import hk.edu.sehs4701.cissas.repository.*;
import hk.edu.sehs4701.cissas.security.AesEncryptionConverter;
import hk.edu.sehs4701.cissas.security.HashService;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.http.MediaType;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

@SpringBootTest(classes = IdentityReviewDatabaseTest.TestConfiguration.class, webEnvironment = SpringBootTest.WebEnvironment.NONE, properties = {
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.show-sql=false",
        "identity-document.encryption.secret=test-only-secret",
        "identity-document.encryption.salt=0123456789abcdef",
        "identity-document.hash.pepper=test-only-pepper"
})
@ActiveProfiles("dev")
@EnabledIfEnvironmentVariable(named = "CISSAS_IDENTITY_TEST_DATABASE_URL", matches = ".+")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class IdentityReviewDatabaseTest {
    @Configuration(proxyBeanMethods = false)
    @EnableAutoConfiguration
    @EntityScan(basePackageClasses = Applicant.class)
    @EnableJpaRepositories(basePackageClasses = ApplicationRepository.class)
    @Import({AdmissionsApplicationService.class, IdentityReviewService.class, AcademicYearService.class,
            ProgrammeLevelService.class, AdminAccountService.class, LoginService.class,
            SuperAdminAcademicYearController.class, IdentityReviewController.class,
            IdentityDocumentService.class, HashService.class, AesEncryptionConverter.class})
    static class TestConfiguration {}

    @MockitoBean private NotificationService notifications;
    @Autowired private AdmissionsApplicationService admissions;
    @Autowired private AcademicYearService academicYears;
    @Autowired private ProgrammeLevelService programmeLevels;
    @Autowired private AdminAccountService adminAccounts;
    @Autowired private SuperAdminAcademicYearController yearController;
    @Autowired private IdentityReviewController identityController;
    @Autowired private LoginService loginService;
    @Autowired private IdentityReviewService reviewService;
    @Autowired private MemberRepository members;
    @Autowired private AdminRepository admins;
    @Autowired private AcademicYearRepository years;
    @Autowired private ProgrammeLevelRepository levels;
    @Autowired private ApplicantRepository applicants;
    @Autowired private ApplicationRepository applications;
    @Autowired private IdentityReviewRepository reviews;
    @Autowired private IdentityReviewLockRepository reviewLock;
    @Autowired private IdentityDocumentRepository documents;
    @Autowired private IdentityDocumentService documentService;
    @Autowired private TransactionTemplate transactions;

    @DynamicPropertySource
    static void dedicatedDatabase(DynamicPropertyRegistry registry) {
        String url = System.getenv("CISSAS_IDENTITY_TEST_DATABASE_URL");
        if (url == null || !url.matches("jdbc:mysql://localhost:3306/cissas_identity_test_[a-z0-9]+\\?.+")) {
            throw new IllegalStateException("A dedicated cissas_identity_test_* database is required");
        }
        registry.add("spring.datasource.url", () -> url);
    }

    @BeforeAll
    void setup() {
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Hong_Kong"));
        AcademicYear year = new AcademicYear();
        year.setName("test-year");
        year.setCreationDate(today);
        year.setStatus(AcademicYearStatus.ACTIVE);
        year = years.saveAndFlush(year);
        ProgrammeLevel level = new ProgrammeLevel();
        level.setLevelCode(LevelCode.S1);
        level.setProgrammeName("Test");
        level.setAcademicYear(year);
        level.setApplicationStartDate(today.minusDays(1));
        level.setApplicationEndDate(today.plusDays(2));
        levels.saveAndFlush(level);
    }

    @Test
    void concurrentExactSubmissionsAcceptOnlyOne() throws Exception {
        Member first = member("exact1");
        Member second = member("exact2");
        ApplicationSubmissionRequest request = request("Exact Applicant", IdentityDocumentType.HKID, "A123456(3)", null);
        List<Integer> statuses = submitTogether(request, first.getId(), request, second.getId());
        assertEquals(1, statuses.stream().filter(code -> code == 201).count());
        assertEquals(1, statuses.stream().filter(code -> code == 409).count());
    }

    @Test
    void memberDocumentRemainsSharedAfterCancellationAndResubmission() {
        Member owner = member("shared-document");
        Long documentId = transactions.execute(status -> {
            Member managed = members.findById(owner.getId()).orElseThrow();
            IdentityDocument document = documentService.createIdentityDocument(
                    IdentityDocumentType.HKID, "J123456(8)", null);
            managed.addIdentityDocument(document);
            return documents.saveAndFlush(document).getId();
        });
        var request = request("Shared Document Applicant", IdentityDocumentType.HKID, "J123456(8)", null);
        assertEquals(201, submit(request, owner.getId()));
        String firstNumber = applicationNumberFor(owner);
        Long applicantId = applications.findByApplicationNumber(firstNumber).orElseThrow().getApplicant().getId();
        IdentityDocument stored = documents.findById(documentId).orElseThrow();
        assertEquals(owner.getId(), stored.getMember().getId());
        assertEquals(applicantId, stored.getApplicant().getId());
        changeStatus(firstNumber, ApplicationStatus.Cancelled);
        assertEquals(201, submit(request, owner.getId()));
        assertEquals(applicantId, applications.findByApplicationNumber(applicationNumberFor(owner))
                .orElseThrow().getApplicant().getId());
        assertEquals(applicantId, documents.findById(documentId).orElseThrow().getApplicant().getId());
    }

    @Test
    void storesMaximumLengthPassportAfterUnicodeUppercaseExpansion() {
        Member owner = member("expanded-passport");
        String number = "\u0390".repeat(512);
        assertEquals(201, submit(request("Expanded Passport", IdentityDocumentType.PASSPORT, number, "HK"),
                owner.getId()));

        String hash = documentService.getDocumentNumberHash(IdentityDocumentType.PASSPORT, number, "HK");
        transactions.executeWithoutResult(status -> {
            IdentityDocument stored = documents.findByDocumentNumberHash(hash).orElseThrow();
            assertEquals(number.toUpperCase(java.util.Locale.ROOT), stored.getDocumentNumber());
        });
        assertEquals(number, applications.findByApplicationNumber(applicationNumberFor(owner))
                .orElseThrow().getApplicantSnapshot().getDocumentNumber());
    }

    @Test
    void databasePagesOnlyPairsWithTheSameNormalizedName() {
        Member first = member("name-filter-first");
        Member different = member("name-filter-different");
        Member matching = member("name-filter-matching");
        assertEquals(201, submit(request("Filter \u0390 Name", IdentityDocumentType.HKID, "K123456(0)", null), first.getId()));
        assertEquals(201, submit(request("Filter I Name", IdentityDocumentType.HKID, "L123456(3)", null), different.getId()));
        assertEquals(201, submit(request("  filter  \u0399\u0308\u0301 name  ", IdentityDocumentType.PASSPORT,
                "NAMEFILTER1", "HK"), matching.getId()));
        String firstNumber = applicationNumberFor(first);
        String differentNumber = applicationNumberFor(different);
        String matchingNumber = applicationNumberFor(matching);
        assertTrue(applications.findByApplicationNumber(matchingNumber).orElseThrow().isManualIdentityReviewRequired());

        int matchingPairs = 0;
        Long afterFirst = null;
        Long afterSecond = null;
        org.springframework.data.domain.Slice<Object[]> page;
        do {
            page = applications.findPotentialReviewPairs(afterFirst, afterSecond,
                    org.springframework.data.domain.PageRequest.of(0, 10));
            for (Object[] pair : page) {
                Application a = (Application) pair[0];
                Application b = (Application) pair[1];
                if (List.of(a.getApplicationNumber(), b.getApplicationNumber()).contains(matchingNumber)) {
                    assertFalse(List.of(a.getApplicationNumber(), b.getApplicationNumber()).contains(differentNumber));
                    if (List.of(a.getApplicationNumber(), b.getApplicationNumber()).contains(firstNumber)) {
                        matchingPairs++;
                    }
                }
            }
            if (page.hasNext()) {
                Object[] last = page.getContent().getLast();
                afterFirst = ((Application) last[0]).getId();
                afterSecond = ((Application) last[1]).getId();
            }
        } while (page.hasNext());
        assertEquals(1, matchingPairs);
    }

    @Test
    void databaseDoesNotPageThroughTwoThousandFiveHundredDifferentNamePairs() {
        Member owner = member("bulk-name-filter");
        Long activeYearId = years.findFirstByStatusOrderByCreationDateDesc(AcademicYearStatus.ACTIVE)
                .orElseThrow().getId();
        ProgrammeLevel level = levels.findAll().stream()
                .filter(p -> p.getLevelCode() == LevelCode.S1 && p.getAcademicYear().getId().equals(activeYearId))
                .findFirst().orElseThrow();
        List<Application> bulk = new java.util.ArrayList<>();
        for (int i = 0; i < 100; i++) {
            Applicant applicant = new Applicant();
            applicant.setMember(owner);
            applicant = applicants.save(applicant);
            IdentityDocumentType type = i < 50 ? IdentityDocumentType.HKID : IdentityDocumentType.PASSPORT;
            Application application = new Application();
            application.setApplicationNumber("BULK-NAME-" + i);
            application.setMember(owner);
            application.setApplicant(applicant);
            application.setProgrammeLevel(level);
            application.setAcademicYear(level.getAcademicYear());
            application.setApplicantSnapshot(new ApplicantSnapshot("Distinct Name " + i,
                    LocalDate.of(2016, 2, 13), "Unspecified", "12345678", "School", type, "test", "HK"));
            bulk.add(application);
        }
        applications.saveAllAndFlush(bulk);
        var ids = bulk.stream().map(Application::getId).collect(java.util.stream.Collectors.toSet());

        Long afterFirst = null;
        Long afterSecond = null;
        org.springframework.data.domain.Slice<Object[]> page;
        int pages = 0;
        do {
            page = applications.findPotentialReviewPairs(afterFirst, afterSecond,
                    org.springframework.data.domain.PageRequest.of(0, 25));
            for (Object[] pair : page) {
                assertFalse(ids.contains(((Application) pair[0]).getId()));
                assertFalse(ids.contains(((Application) pair[1]).getId()));
            }
            if (page.hasNext()) {
                Object[] last = page.getContent().getLast();
                afterFirst = ((Application) last[0]).getId();
                afterSecond = ((Application) last[1]).getId();
            }
            assertTrue(++pages < 1000, "Pagination must terminate");
        } while (page.hasNext());
    }

    @Test
    void anotherMemberSeesSameResponseForActiveAndCancelledApplication() throws Exception {
        Member owner = member("privacy-owner");
        Member other = member("privacy-other");
        var application = request("Privacy Applicant", IdentityDocumentType.PASSPORT, "PRIVACY1", "HK");
        assertEquals(201, submit(application, owner.getId()));
        String number = applicationNumberFor(owner);
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("authenticatedUserRole", "ROLE_MEMBER");
        session.setAttribute("authenticatedUserId", other.getId());
        var mvc = MockMvcBuilders.standaloneSetup(new AdmissionsApplicationController(admissions)).build();
        String body = """
                {"levelCode":"S1","applicantFullName":"Privacy Applicant",
                "applicantIdentityDocumentNumber":"PRIVACY1","applicantIdentityDocumentType":"PASSPORT",
                "applicantIdentityDocumentIssuingCountry":"HK","dateOfBirth":"2018-01-01",
                "gender":"Unspecified","phone":"12345678","previousSchool":"School"}
                """;
        var active = mvc.perform(post("/api/member/applications").session(session)
                .contentType(MediaType.APPLICATION_JSON).content(body)).andReturn().getResponse();
        changeStatus(number, ApplicationStatus.Cancelled);
        var cancelled = mvc.perform(post("/api/member/applications").session(session)
                .contentType(MediaType.APPLICATION_JSON).content(body)).andReturn().getResponse();
        assertEquals(409, active.getStatus());
        assertEquals(active.getStatus(), cancelled.getStatus());
        assertEquals(active.getErrorMessage(), cancelled.getErrorMessage());
        assertEquals(active.getContentAsString(), cancelled.getContentAsString());
        assertFalse(active.getErrorMessage().contains(number));
    }

    @Test
    void newYearUsesSeparateProgrammeWithoutMovingExistingApplication() {
        Member member = member("year");
        ApplicationSubmissionRequest request = request("Year Applicant", IdentityDocumentType.PASSPORT, "YEAR1", "HK");
        assertEquals(201, submit(request, member.getId()));
        Long activeYearId = years.findFirstByStatusOrderByCreationDateDesc(AcademicYearStatus.ACTIVE)
                .orElseThrow().getId();
        ProgrammeLevel level = levels.findAll().stream()
                .filter(p -> p.getLevelCode() == LevelCode.S1 && p.getAcademicYear().getId().equals(activeYearId))
                .findFirst().orElseThrow();
        AcademicYear original = years.findById(level.getAcademicYear().getId()).orElseThrow();
        Long originalId = original.getId();
        Application first = applications.findAll().stream()
                .filter(a -> a.getMember().getId().equals(member.getId())).findFirst().orElseThrow();
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Hong_Kong"));
        AcademicYear next = new AcademicYear();
        next.setName("test-next-year");
        next.setCreationDate(today);
        next.setStatus(AcademicYearStatus.ACTIVE);
        try {
            original.setStatus(AcademicYearStatus.ARCHIVED);
            years.saveAndFlush(original);
            next = years.saveAndFlush(next);
            var nextLevel = programmeLevels.create(next.getId(), level.getLevelCode(), level.getProgrammeName(),
                    level.getSeatQuota(), level.getApplicationStartDate(), level.getApplicationEndDate());
            assertEquals(201, submit(request, member.getId()));
            assertEquals(originalId, applications.findById(first.getId()).orElseThrow().getAcademicYear().getId());
            assertEquals(level.getId(), applications.findById(first.getId()).orElseThrow().getProgrammeLevel().getId());
            assertNotEquals(level.getId(), nextLevel.id());
        } finally {
            original.setStatus(AcademicYearStatus.ACTIVE);
            years.saveAndFlush(original);
            if (next.getId() != null) {
                next.setStatus(AcademicYearStatus.ARCHIVED);
                years.saveAndFlush(next);
            }
        }
    }

    @Test
    void concurrentDuplicateYearCreationReturnsConflict() throws Exception {
        var executor = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Hong_Kong"));
        try {
            var tasks = java.util.stream.IntStream.range(0, 2).mapToObj(index -> executor.submit(() -> {
                start.await();
                try {
                    academicYears.create("concurrent-year");
                    return 201;
                } catch (ResponseStatusException ex) {
                    return ex.getStatusCode().value();
                }
            })).toList();
            start.countDown();
            List<Integer> results = List.of(tasks.get(0).get(10, TimeUnit.SECONDS),
                    tasks.get(1).get(10, TimeUnit.SECONDS));
            assertEquals(1, results.stream().filter(code -> code == 201).count());
            assertEquals(1, results.stream().filter(code -> code == 409).count());
        } finally {
            executor.shutdownNow();
            years.findAll().stream().filter(year -> year.getName().equals("concurrent-year"))
                    .forEach(years::delete);
        }
    }

    @Test
    void concurrentActivationsAllowOnlyOneActiveYear() throws Exception {
        AcademicYear original = years.findAll().stream()
                .filter(year -> year.getStatus() == AcademicYearStatus.ACTIVE).findFirst().orElseThrow();
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Hong_Kong"));
        AcademicYear first = new AcademicYear();
        first.setName("activation-test-1");
        first.setCreationDate(today);
        first.setStatus(AcademicYearStatus.PLANNING);
        AcademicYear second = new AcademicYear();
        second.setName("activation-test-2");
        second.setCreationDate(today);
        second.setStatus(AcademicYearStatus.PLANNING);
        original.setStatus(AcademicYearStatus.ARCHIVED);
        years.saveAndFlush(original);
        first = years.saveAndFlush(first);
        second = years.saveAndFlush(second);
        Long firstId = first.getId();
        Long secondId = second.getId();
        var executor = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            var tasks = List.of(firstId, secondId).stream().map(id -> executor.submit(() -> {
                start.await();
                try {
                    academicYears.updateLifecycle(id, AcademicYearStatus.ACTIVE);
                    return 200;
                } catch (ResponseStatusException ex) {
                    return ex.getStatusCode().value();
                }
            })).toList();
            start.countDown();
            List<Integer> results = List.of(tasks.get(0).get(10, TimeUnit.SECONDS),
                    tasks.get(1).get(10, TimeUnit.SECONDS));
            assertEquals(1, results.stream().filter(code -> code == 200).count());
            assertEquals(1, results.stream().filter(code -> code == 409).count());
        } finally {
            executor.shutdownNow();
            years.deleteById(firstId);
            years.deleteById(secondId);
            original.setStatus(AcademicYearStatus.ACTIVE);
            years.saveAndFlush(original);
        }
    }

    @Test
    void adminCanReviewUnflaggedDifferentNamesAndAccessSubmittedEvidence() {
        Member first = member("manual1");
        Member second = member("manual2");
        ApplicationSubmissionRequest one = request("Original Name", IdentityDocumentType.PASSPORT, " MANUAL1 ", "HK");
        ApplicationSubmissionRequest two = request("Corrected Name", IdentityDocumentType.PASSPORT, "MANUAL2", "HK");
        assertEquals(201, submit(one, first.getId()));
        assertEquals(201, submit(two, second.getId()));
        String firstNumber = applications.findAll().stream()
                .filter(a -> a.getMember().getId().equals(first.getId())).findFirst().orElseThrow().getApplicationNumber();
        String secondNumber = applications.findAll().stream()
                .filter(a -> a.getMember().getId().equals(second.getId())).findFirst().orElseThrow().getApplicationNumber();
        Admin admin = new Admin();
        admin.setEmail("manual-admin@example.test");
        admin.setFirstName("Admin");
        admin.setLastName("Manual");
        admin.setPasswordHash("test");
        admin = admins.saveAndFlush(admin);
        assertEquals(" MANUAL1 ", reviewService.evidence(admin.getId(), firstNumber).documentNumber());
        reviewService.review(admin.getId(), firstNumber, secondNumber, true);
        changeStatus(firstNumber, ApplicationStatus.Cancelled);
        assertEquals(409, submit(one, first.getId()));
    }

    @Test
    void pendingPagesExposeMatchesEvenWhenSomePagesAreEmpty() {
        Member first = member("page1");
        Member second = member("page2");
        assertEquals(201, submit(request("Page Applicant", IdentityDocumentType.HKID, "C123456(9)", null), first.getId()));
        assertEquals(201, submit(request("Page Applicant", IdentityDocumentType.PASSPORT, "PAGE1", "HK"), second.getId()));
        Admin admin = new Admin();
        admin.setEmail("page-admin@example.test");
        admin.setFirstName("Admin");
        admin.setLastName("Page");
        admin.setPasswordHash("test");
        admin = admins.saveAndFlush(admin);
        boolean found = false;
        int pages = 0;
        String cursor = null;
        CandidatePage result;
        do {
            result = reviewService.pending(admin.getId(), cursor, 1);
            assertTrue(result.items().size() <= 1);
            found |= result.items().stream().anyMatch(pair -> pair.flagged().fullName().equals("Page Applicant"));
            assertTrue(++pages < 1000, "Pagination must terminate");
            cursor = result.nextCursor();
        } while (cursor != null);
        assertTrue(found);
    }

    @Test
    void cursorContinuesAfterPreviousPairIsReviewed() {
        Member first = member("cursor1");
        Member second = member("cursor2");
        Member third = member("cursor3");
        Member fourth = member("cursor4");
        assertEquals(201, submit(request("Cursor Alpha", IdentityDocumentType.HKID, "E123456(4)", null), first.getId()));
        assertEquals(201, submit(request("Cursor Alpha", IdentityDocumentType.PASSPORT, "CURSOR1", "HK"), second.getId()));
        assertEquals(201, submit(request("Cursor Beta", IdentityDocumentType.HKID, "F123456(7)", null), third.getId()));
        assertEquals(201, submit(request("Cursor Beta", IdentityDocumentType.PASSPORT, "CURSOR2", "HK"), fourth.getId()));
        Admin admin = new Admin();
        admin.setEmail("cursor-admin@example.test");
        admin.setFirstName("Admin");
        admin.setLastName("Cursor");
        admin.setPasswordHash("test");
        admin = admins.saveAndFlush(admin);
        String cursor = null;
        boolean reviewedFirst = false;
        boolean foundSecond = false;
        int pages = 0;
        do {
            var result = reviewService.pending(admin.getId(), cursor, 1);
            for (var pair : result.items()) {
                if (pair.flagged().fullName().equals("Cursor Alpha")) {
                    reviewService.review(admin.getId(), pair.flagged().applicationNumber(),
                            pair.possibleMatch().applicationNumber(), false);
                    reviewedFirst = true;
                }
                if (pair.flagged().fullName().equals("Cursor Beta")) {
                    assertTrue(reviewedFirst);
                    foundSecond = true;
                }
            }
            cursor = result.nextCursor();
            assertTrue(++pages < 1000, "Pagination must terminate");
        } while (cursor != null);
        assertTrue(reviewedFirst);
        assertTrue(foundSecond);
    }

    @Test
    void resubmissionSkipsResolvedMatchButFlagsAnotherUnresolvedMatch() {
        Member hkidMember = member("resolved1");
        Member passportMember = member("resolved2");
        Member otherMember = member("resolved3");
        var hkid = request("Resolved Applicant", IdentityDocumentType.HKID, "G123456(A)", null);
        var passport = request("Resolved Applicant", IdentityDocumentType.PASSPORT, "RESOLVED1", "HK");
        assertEquals(201, submit(hkid, hkidMember.getId()));
        assertEquals(201, submit(passport, passportMember.getId()));
        String hkidNumber = applicationNumberFor(hkidMember);
        String passportNumber = applicationNumberFor(passportMember);
        Admin admin = new Admin();
        admin.setEmail("resolved-admin@example.test");
        admin.setFirstName("Admin");
        admin.setLastName("Resolved");
        admin.setPasswordHash("test");
        admin = admins.saveAndFlush(admin);
        reviewService.review(admin.getId(), passportNumber, hkidNumber, false);
        changeStatus(passportNumber, ApplicationStatus.Cancelled);
        clearInvocations(notifications);
        assertEquals(201, submit(passport, passportMember.getId()));
        String resubmittedNumber = applicationNumberFor(passportMember);
        assertFalse(applications.findByApplicationNumber(resubmittedNumber).orElseThrow().isManualIdentityReviewRequired());
        verify(notifications, never()).notifyAdminOfIdentityReview(any(), any(), anyString());

        assertEquals(201, submit(request("Resolved Applicant", IdentityDocumentType.HKID, "H123456(2)", null),
                otherMember.getId()));
        changeStatus(resubmittedNumber, ApplicationStatus.Cancelled);
        clearInvocations(notifications);
        assertEquals(201, submit(passport, passportMember.getId()));
        assertTrue(applications.findByApplicationNumber(applicationNumberFor(passportMember))
                .orElseThrow().isManualIdentityReviewRequired());
        verify(notifications).notifyAdminOfIdentityReview(any(), any(), anyString());
    }

    @Test
    void groupDecisionSuppressesAlertsAndQueueButKeepsUnresolvedMatches() {
        Member first = member("group-first");
        Member linked = member("group-linked");
        Member different = member("group-different");
        Member unresolved = member("group-unresolved");
        var firstRequest = request("Group Applicant", IdentityDocumentType.PASSPORT, "GROUP1", "HK");
        assertEquals(201, submit(firstRequest, first.getId()));
        assertEquals(201, submit(request("Group Applicant", IdentityDocumentType.PASSPORT, "GROUP2", "HK"), linked.getId()));
        assertEquals(201, submit(request("Group Applicant", IdentityDocumentType.HKID, "U123456(8)", null), different.getId()));
        String firstNumber = applicationNumberFor(first);
        String linkedNumber = applicationNumberFor(linked);
        String differentNumber = applicationNumberFor(different);
        Admin admin = new Admin();
        admin.setEmail("group-admin@example.test");
        admin.setFirstName("Admin");
        admin.setLastName("Group");
        admin.setPasswordHash("test");
        admin = admins.saveAndFlush(admin);
        reviewService.review(admin.getId(), firstNumber, linkedNumber, true);
        reviewService.review(admin.getId(), linkedNumber, differentNumber, false);
        changeStatus(firstNumber, ApplicationStatus.Cancelled);
        changeStatus(linkedNumber, ApplicationStatus.Cancelled);
        clearInvocations(notifications);
        assertEquals(201, submit(firstRequest, first.getId()));
        String secondNumber = applicationNumberFor(first);
        assertFalse(applications.findByApplicationNumber(secondNumber).orElseThrow().isManualIdentityReviewRequired());
        verify(notifications, never()).notifyAdminOfIdentityReview(any(), any(), anyString());

        assertEquals(201, submit(request("Group Applicant", IdentityDocumentType.HKID, "V123456(0)", null), unresolved.getId()));
        String unresolvedNumber = applicationNumberFor(unresolved);
        changeStatus(secondNumber, ApplicationStatus.Cancelled);
        clearInvocations(notifications);
        assertEquals(201, submit(firstRequest, first.getId()));
        String thirdNumber = applicationNumberFor(first);
        assertTrue(applications.findByApplicationNumber(thirdNumber).orElseThrow().isManualIdentityReviewRequired());
        verify(notifications).notifyAdminOfIdentityReview(any(), any(), anyString());

        List<Candidate> matches = new java.util.ArrayList<>();
        String cursor = null;
        do {
            var page = reviewService.pending(admin.getId(), cursor, 2);
            matches.addAll(page.items());
            cursor = page.nextCursor();
        } while (cursor != null);
        var firstGroupNumbers = List.of(firstNumber, secondNumber, thirdNumber, linkedNumber);
        assertFalse(matches.stream().anyMatch(pair ->
                (firstGroupNumbers.contains(pair.flagged().applicationNumber()) && pair.possibleMatch().applicationNumber().equals(differentNumber))
                        || (pair.flagged().applicationNumber().equals(differentNumber) && firstGroupNumbers.contains(pair.possibleMatch().applicationNumber()))));
        assertTrue(matches.stream().anyMatch(pair ->
                (pair.flagged().applicationNumber().equals(thirdNumber) && pair.possibleMatch().applicationNumber().equals(unresolvedNumber))
                        || (pair.flagged().applicationNumber().equals(unresolvedNumber) && pair.possibleMatch().applicationNumber().equals(thirdNumber))));
    }

    @Test
    void correctionReopensAnUnflaggedPairInTheQueue() {
        Member first = member("reopen-first");
        Member linked = member("reopen-linked");
        Member different = member("reopen-different");
        var firstRequest = request("Reopen Applicant", IdentityDocumentType.PASSPORT, "REOPEN1", "HK");
        assertEquals(201, submit(firstRequest, first.getId()));
        assertEquals(201, submit(request("Reopen Applicant", IdentityDocumentType.PASSPORT, "REOPEN2", "HK"), linked.getId()));
        String firstNumber = applicationNumberFor(first);
        String linkedNumber = applicationNumberFor(linked);
        Admin admin = new Admin();
        admin.setEmail("reopen-admin@example.test");
        admin.setFirstName("Admin");
        admin.setLastName("Reopen");
        admin.setPasswordHash("test");
        admin = admins.saveAndFlush(admin);
        reviewService.review(admin.getId(), firstNumber, linkedNumber, true);
        changeStatus(firstNumber, ApplicationStatus.Cancelled);
        changeStatus(linkedNumber, ApplicationStatus.Cancelled);
        assertEquals(201, submit(request("Reopen Applicant", IdentityDocumentType.HKID, "W123456(3)", null), different.getId()));
        String differentNumber = applicationNumberFor(different);
        assertFalse(applications.findByApplicationNumber(differentNumber).orElseThrow().isManualIdentityReviewRequired());
        reviewService.review(admin.getId(), linkedNumber, differentNumber, false);
        assertEquals(201, submit(firstRequest, first.getId()));
        String resubmittedNumber = applicationNumberFor(first);
        assertFalse(applications.findByApplicationNumber(resubmittedNumber).orElseThrow().isManualIdentityReviewRequired());

        assertTrue(pendingPair(admin.getId(), resubmittedNumber, differentNumber).isEmpty());
        reviewService.review(admin.getId(), firstNumber, linkedNumber, false);
        assertEquals(1, pendingPair(admin.getId(), resubmittedNumber, differentNumber).size());
        changeStatus(resubmittedNumber, ApplicationStatus.Cancelled);
        changeStatus(differentNumber, ApplicationStatus.Cancelled);
        assertTrue(pendingPair(admin.getId(), resubmittedNumber, differentNumber).isEmpty());
        changeStatus(resubmittedNumber, ApplicationStatus.Pending);
        changeStatus(differentNumber, ApplicationStatus.Pending);
        assertEquals(1, pendingPair(admin.getId(), resubmittedNumber, differentNumber).size());
        reviewService.review(admin.getId(), resubmittedNumber, differentNumber, false);
        assertTrue(pendingPair(admin.getId(), resubmittedNumber, differentNumber).isEmpty());
    }

    private List<Candidate> pendingPair(Long adminId, String first, String second) {
        List<Candidate> result = new java.util.ArrayList<>();
        String cursor = null;
        int pages = 0;
        do {
            var page = reviewService.pending(adminId, cursor, 2);
            result.addAll(page.items().stream().filter(pair ->
                    (pair.flagged().applicationNumber().equals(first) && pair.possibleMatch().applicationNumber().equals(second))
                            || (pair.flagged().applicationNumber().equals(second) && pair.possibleMatch().applicationNumber().equals(first)))
                    .toList());
            cursor = page.nextCursor();
            assertTrue(++pages < 1000, "Pagination must terminate");
        } while (cursor != null);
        return result;
    }

    private String applicationNumberFor(Member member) {
        return applications.findAll().stream()
                .filter(a -> a.getMember().getId().equals(member.getId()))
                .max(java.util.Comparator.comparing(Application::getSubmittedAt))
                .orElseThrow().getApplicationNumber();
    }
    @Test
    void submissionWaitsForIdentityReviewTransaction() throws Exception {
        Member member = member("wait");
        ApplicationSubmissionRequest request = request("Wait Applicant", IdentityDocumentType.PASSPORT, "WAIT1", "HK");
        var executor = Executors.newSingleThreadExecutor();
        CountDownLatch started = new CountDownLatch(1);
        AtomicReference<Future<Integer>> submission = new AtomicReference<>();
        try {
            transactions.executeWithoutResult(status -> {
                reviewLock.acquire();
                submission.set(executor.submit(() -> {
                    started.countDown();
                    return submit(request, member.getId());
                }));
                try {
                    assertTrue(started.await(5, TimeUnit.SECONDS));
                    assertThrows(TimeoutException.class, () -> submission.get().get(300, TimeUnit.MILLISECONDS));
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    fail(exception);
                }
            });
            assertEquals(201, submission.get().get(10, TimeUnit.SECONDS));
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void accountLockDoesNotBlockAdmissionsLock() throws Exception {
        transactions.executeWithoutResult(status -> {
            reviewLock.acquire();
            reviewLock.acquireAccountLock();
        });
        var executor = Executors.newSingleThreadExecutor();
        try {
            transactions.executeWithoutResult(status -> {
                reviewLock.acquireAccountLock();
                Future<?> admission = executor.submit(() ->
                        transactions.executeWithoutResult(ignored -> reviewLock.acquire()));
                try {
                    admission.get(5, TimeUnit.SECONDS);
                } catch (Exception exception) {
                    fail(exception);
                }
            });
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void officerResetRevokesOldSessionsInDatabase() {
        var officer = adminAccounts.createOfficer(new AccountRequest(
                "recovery-officer@example.test", "Test", "Officer", "initial-password"));
        var login = loginService.login(officer.email(), "initial-password", LoginAccountType.ADMIN);
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("authenticatedUserId", login.response().id());
        session.setAttribute("authenticatedUserRole", login.response().role().name());
        session.setAttribute("adminCredentialVersion", login.credentialVersion());
        org.springframework.mock.web.MockHttpServletRequest request = new org.springframework.mock.web.MockHttpServletRequest();
        request.setSession(session);
        adminAccounts.changePassword(request, new PasswordChangeRequest(
                "initial-password", "private-password"));
        assertThrows(ResponseStatusException.class, () -> adminAccounts.requireAdmin(request));
        var freshLogin = loginService.login(officer.email(), "private-password", LoginAccountType.ADMIN);
        session.setAttribute("adminCredentialVersion", freshLogin.credentialVersion());
        assertEquals(login.response().id(), adminAccounts.requireAdmin(request));
        adminAccounts.resetOfficerPassword(Long.valueOf(officer.id()),
                new PasswordResetRequest("temporary-password"));
        assertThrows(ResponseStatusException.class, () -> adminAccounts.requireAdmin(request));
        assertThrows(ResponseStatusException.class,
                () -> loginService.login(officer.email(), "private-password", LoginAccountType.ADMIN));
        assertEquals(2L, loginService.login(officer.email(), "temporary-password",
                LoginAccountType.ADMIN).credentialVersion());
    }

    @Test
    void recoveryWaitsForConcurrentPasswordChangeAndRevokesItsSession() throws Exception {
        Admin superAdmin = new Admin();
        superAdmin.setEmail("concurrent-recovery@example.test");
        superAdmin.setFirstName("Test");
        superAdmin.setLastName("Super");
        superAdmin.setRole(ActorRole.ROLE_SUPER_ADMIN);
        superAdmin.setPasswordHash("unused");
        Long id = admins.saveAndFlush(superAdmin).getId();
        var executor = Executors.newSingleThreadExecutor();
        CountDownLatch started = new CountDownLatch(1);
        AtomicReference<Future<?>> recovery = new AtomicReference<>();
        try {
            transactions.executeWithoutResult(status -> {
                Admin locked = admins.findLockedById(id).orElseThrow();
                recovery.set(executor.submit(() -> {
                    started.countDown();
                    adminAccounts.recoverSuperAdmin("concurrent-recovery@example.test", "recovery-password");
                }));
                try {
                    assertTrue(started.await(5, TimeUnit.SECONDS));
                    assertThrows(TimeoutException.class, () -> recovery.get().get(300, TimeUnit.MILLISECONDS));
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    fail(exception);
                }
                locked.setCredentialVersion(1);
                admins.saveAndFlush(locked);
            });
            recovery.get().get(10, TimeUnit.SECONDS);
            assertEquals(2, admins.findById(id).orElseThrow().getCredentialVersion());
            MockHttpSession session = new MockHttpSession();
            session.setAttribute("authenticatedUserId", id);
            session.setAttribute("authenticatedUserRole", "ROLE_SUPER_ADMIN");
            session.setAttribute("adminCredentialVersion", 1L);
            org.springframework.mock.web.MockHttpServletRequest request = new org.springframework.mock.web.MockHttpServletRequest();
            request.setSession(session);
            assertThrows(ResponseStatusException.class, () -> adminAccounts.requirePasswordSession(request));
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void revokedSessionCannotCreateYearAfterWaitingForAccountLock() throws Exception {
        Admin superAdmin = new Admin();
        superAdmin.setEmail("year-revocation@example.test");
        superAdmin.setFirstName("Test");
        superAdmin.setLastName("Super");
        superAdmin.setRole(ActorRole.ROLE_SUPER_ADMIN);
        superAdmin.setPasswordHash("unused");
        Long id = admins.saveAndFlush(superAdmin).getId();
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("authenticatedUserId", id);
        session.setAttribute("authenticatedUserRole", "ROLE_SUPER_ADMIN");
        session.setAttribute("adminCredentialVersion", 0L);
        var request = new org.springframework.mock.web.MockHttpServletRequest();
        request.setSession(session);
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Hong_Kong"));
        var create = new CreateAcademicYearRequest("revoked-year");
        var executor = Executors.newSingleThreadExecutor();
        CountDownLatch started = new CountDownLatch(1);
        AtomicReference<Future<?>> operation = new AtomicReference<>();
        try {
            transactions.executeWithoutResult(status -> {
                Admin locked = admins.findLockedById(id).orElseThrow();
                operation.set(executor.submit(() -> {
                    started.countDown();
                    assertEquals(org.springframework.http.HttpStatus.FORBIDDEN,
                            assertThrows(ResponseStatusException.class,
                                    () -> yearController.create(create, request)).getStatusCode());
                }));
                try {
                    assertTrue(started.await(5, TimeUnit.SECONDS));
                    assertThrows(TimeoutException.class, () -> operation.get().get(300, TimeUnit.MILLISECONDS));
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    fail(exception);
                }
                locked.setCredentialVersion(1);
                admins.saveAndFlush(locked);
            });
            operation.get().get(10, TimeUnit.SECONDS);
            assertFalse(years.existsByName("revoked-year"));
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void revokedSessionCannotReadEvidenceAfterWaitingForAccountLock() throws Exception {
        Member member = member("revoked-read");
        assertEquals(201, submit(request("Revoked Read", IdentityDocumentType.PASSPORT,
                "REVOKED-READ", "HK"), member.getId()));
        String number = applications.findAll().stream()
                .filter(application -> application.getMember().getId().equals(member.getId()))
                .findFirst().orElseThrow().getApplicationNumber();
        Admin admin = new Admin();
        admin.setEmail("read-revocation@example.test");
        admin.setFirstName("Test");
        admin.setLastName("Officer");
        admin.setPasswordHash("unused");
        Long id = admins.saveAndFlush(admin).getId();
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("authenticatedUserId", id);
        session.setAttribute("authenticatedUserRole", "ROLE_ADMIN");
        session.setAttribute("adminCredentialVersion", 0L);
        var request = new org.springframework.mock.web.MockHttpServletRequest();
        request.setSession(session);
        assertEquals("REVOKED-READ", identityController.evidence(number, request).getBody().documentNumber());

        var executor = Executors.newSingleThreadExecutor();
        CountDownLatch started = new CountDownLatch(1);
        AtomicReference<Future<?>> operation = new AtomicReference<>();
        try {
            transactions.executeWithoutResult(status -> {
                Admin locked = admins.findLockedById(id).orElseThrow();
                operation.set(executor.submit(() -> {
                    started.countDown();
                    assertEquals(org.springframework.http.HttpStatus.FORBIDDEN,
                            assertThrows(ResponseStatusException.class,
                                    () -> identityController.evidence(number, request)).getStatusCode());
                }));
                try {
                    assertTrue(started.await(5, TimeUnit.SECONDS));
                    assertThrows(TimeoutException.class, () -> operation.get().get(300, TimeUnit.MILLISECONDS));
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    fail(exception);
                }
                locked.setCredentialVersion(1);
                admins.saveAndFlush(locked);
            });
            operation.get().get(10, TimeUnit.SECONDS);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void concurrentCrossDocumentSubmissionsAreFlaggedAndConfirmedAcrossRecords() throws Exception {
        Member first = member("cross1");
        Member second = member("cross2");
        ApplicationSubmissionRequest hkid = request("Cross Applicant", IdentityDocumentType.HKID, "B123456(6)", null);
        ApplicationSubmissionRequest passport = request("Cross Applicant", IdentityDocumentType.PASSPORT, "P-TEST", "HK");
        assertEquals(List.of(201, 201), submitTogether(hkid, first.getId(), passport, second.getId()));

        Admin admin = new Admin();
        admin.setEmail("identity-admin@example.test");
        admin.setFirstName("Admin");
        admin.setLastName("Test");
        admin.setPasswordHash("test");
        admin = admins.saveAndFlush(admin);
        List<Candidate> pending = new java.util.ArrayList<>();
        String cursor = null;
        do {
            var page = reviewService.pending(admin.getId(), cursor, 100);
            pending.addAll(page.items().stream()
                    .filter(candidate -> candidate.flagged().fullName().equals("Cross Applicant")).toList());
            cursor = page.nextCursor();
        } while (cursor != null);
        assertEquals(1, pending.size());
        var pair = pending.getFirst();
        reviewService.review(admin.getId(), pair.flagged().applicationNumber(),
                pair.possibleMatch().applicationNumber(), true);
        assertTrue(reviewService.history(admin.getId(), null, 100).items().stream()
                .anyMatch(review -> review.current() && review.samePerson()
                        && review.flaggedApplicationNumber().equals(pair.flagged().applicationNumber())));
        var decision = reviewService.history(admin.getId(), null, 100).items().getFirst();
        assertEquals(pair.flagged().applicationNumber(), decision.flaggedApplicationNumber());
        assertEquals(pair.possibleMatch().applicationNumber(), decision.candidateApplicationNumber());

        String hkidNumber = pair.flagged().documentType() == IdentityDocumentType.HKID
                ? pair.flagged().applicationNumber() : pair.possibleMatch().applicationNumber();
        String passportNumber = pair.flagged().documentType() == IdentityDocumentType.PASSPORT
                ? pair.flagged().applicationNumber() : pair.possibleMatch().applicationNumber();
        changeStatus(hkidNumber, ApplicationStatus.Cancelled);
        assertEquals(409, submit(hkid, first.getId()));
        changeStatus(hkidNumber, ApplicationStatus.Pending);
        changeStatus(passportNumber, ApplicationStatus.Cancelled);
        assertEquals(409, submit(passport, second.getId()));

        reviewService.review(admin.getId(), pair.flagged().applicationNumber(),
                pair.possibleMatch().applicationNumber(), false);
        var pairDecisions = reviewService.history(admin.getId(), null, 100).items().stream()
                .filter(item -> item.flaggedApplicationNumber().equals(pair.flagged().applicationNumber())
                        && item.candidateApplicationNumber().equals(pair.possibleMatch().applicationNumber()))
                .toList();
        assertEquals(2, pairDecisions.size());
        assertFalse(pairDecisions.stream().filter(Decision::current).findFirst().orElseThrow().samePerson());
        assertEquals(201, submit(passport, second.getId()));
    }

    @Test
    void historyCursorReturnsEveryDecisionWhenNewDecisionIsAdded() {
        Member first = member("history1");
        Member second = member("history2");
        Member third = member("history3");
        assertEquals(201, submit(request("History One", IdentityDocumentType.PASSPORT, "HISTORY1", "HK"), first.getId()));
        assertEquals(201, submit(request("History Two", IdentityDocumentType.PASSPORT, "HISTORY2", "HK"), second.getId()));
        assertEquals(201, submit(request("History Three", IdentityDocumentType.PASSPORT, "HISTORY3", "HK"), third.getId()));
        Admin admin = new Admin();
        admin.setEmail("history-admin@example.test");
        admin.setFirstName("Admin");
        admin.setLastName("History");
        admin.setPasswordHash("test");
        admin = admins.saveAndFlush(admin);
        Long adminId = admin.getId();
        String one = applicationNumberFor(first);
        String two = applicationNumberFor(second);
        String three = applicationNumberFor(third);
        reviewService.review(adminId, one, two, false);
        reviewService.review(adminId, one, two, true);
        var firstPage = reviewService.history(adminId, null, 1);
        assertEquals(one, firstPage.items().getFirst().flaggedApplicationNumber());
        assertTrue(firstPage.items().getFirst().current());
        assertNotNull(firstPage.nextCursor());
        reviewService.review(adminId, two, three, false);
        var secondPage = reviewService.history(adminId, firstPage.nextCursor(), 1);
        assertEquals(one, secondPage.items().getFirst().flaggedApplicationNumber());
        assertFalse(secondPage.items().getFirst().current());
        assertNotEquals(firstPage.items().getFirst(), secondPage.items().getFirst());
        assertEquals(two, reviewService.history(adminId, null, 1).items().getFirst().flaggedApplicationNumber());
    }

    private List<Integer> submitTogether(ApplicationSubmissionRequest first, Long firstMember,
                                         ApplicationSubmissionRequest second, Long secondMember) throws Exception {
        var executor = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Future<Integer> one = executor.submit(() -> { start.await(); return submit(first, firstMember); });
            Future<Integer> two = executor.submit(() -> { start.await(); return submit(second, secondMember); });
            start.countDown();
            return List.of(one.get(), two.get());
        } finally {
            executor.shutdownNow();
        }
    }

    private int submit(ApplicationSubmissionRequest request, Long memberId) {
        try {
            admissions.submitApplication(request, memberId);
            return 201;
        } catch (ResponseStatusException exception) {
            return exception.getStatusCode().value();
        }
    }

    private Member member(String name) {
        Member member = new Member();
        member.setUsername(name);
        member.setEmail(name + "@example.test");
        member.setPasswordHash("test");
        member.setFirstName("Test");
        member.setLastName(name);
        member.setAccountStatus(AccountStatus.ACTIVE);
        return members.saveAndFlush(member);
    }

    private void changeStatus(String number, ApplicationStatus status) {
        Application application = applications.findByApplicationNumber(number).orElseThrow();
        application.setStatus(status);
        applications.saveAndFlush(application);
    }

    private ApplicationSubmissionRequest request(String name, IdentityDocumentType type, String number, String country) {
        return new ApplicationSubmissionRequest(LevelCode.S1, name, number, type, country,
                LocalDate.of(2018, 1, 1), "Unspecified", "12345678", "School");
    }
}
