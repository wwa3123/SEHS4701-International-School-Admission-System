package hk.edu.sehs4701.cissas.service;

import hk.edu.sehs4701.cissas.dto.request.ApplicationSubmissionRequest;
import hk.edu.sehs4701.cissas.dto.request.AccountRequest;
import hk.edu.sehs4701.cissas.dto.request.CreateAcademicYearRequest;
import hk.edu.sehs4701.cissas.dto.request.PasswordChangeRequest;
import hk.edu.sehs4701.cissas.dto.request.PasswordResetRequest;
import hk.edu.sehs4701.cissas.dto.request.OfferDecisionRequest;
import hk.edu.sehs4701.cissas.dto.response.Candidate;
import hk.edu.sehs4701.cissas.dto.response.CandidatePage;
import hk.edu.sehs4701.cissas.dto.response.Decision;
import hk.edu.sehs4701.cissas.controller.api.member.AdmissionsApplicationController;
import hk.edu.sehs4701.cissas.controller.api.admin.ApplicationResultController;
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
                ApplicationResultService.class, ApplicationResultController.class, EmailService.class,
                ProgrammeLevelService.class, AdminAccountService.class, LoginService.class,
                SuperAdminAcademicYearController.class,
            IdentityDocumentService.class, HashService.class, AesEncryptionConverter.class})
    static class TestConfiguration {}

    @MockitoBean private NotificationService notifications;
    @Autowired private AdmissionsApplicationService admissions;
    @Autowired private AcademicYearService academicYears;
    @Autowired private ProgrammeLevelService programmeLevels;
    @Autowired private AdminAccountService adminAccounts;
    @Autowired private SuperAdminAcademicYearController yearController;
    @Autowired private ApplicationResultController applicationController;
    @Autowired private LoginService loginService;
    @Autowired private IdentityReviewService reviewService;
    @Autowired private ApplicationResultService results;
    @Autowired private EmailService emails;
    @Autowired private EmailLogRepository emailLogs;
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
    void cancelledApplicationsAreExcludedFromDuplicateDetectionAndReview() {
        Member first = member("cancelled-match-first");
        Member second = member("cancelled-match-second");
        Member third = member("cancelled-match-third");
        assertEquals(201, submit(request("Cancelled Match", IdentityDocumentType.PASSPORT,
                "CANCELMATCH1", "HK"), first.getId()));
        String firstNumber = applicationNumberFor(first);
        assertEquals(201, submit(request("Cancelled Match", IdentityDocumentType.PASSPORT,
                "CANCELMATCH2", "HK"), second.getId()));
        String secondNumber = applicationNumberFor(second);
        Application secondApplication = applications.findByApplicationNumber(secondNumber).orElseThrow();
        assertTrue(secondApplication.isManualIdentityReviewRequired());
        assertEquals(Boolean.TRUE, transactions.execute(status -> reviewService.hasPossibleDuplicate(
                applications.findByApplicationNumber(secondNumber).orElseThrow())));

        changeStatus(firstNumber, ApplicationStatus.Cancelled);
        assertEquals(Boolean.FALSE, transactions.execute(status -> reviewService.hasPossibleDuplicate(
                applications.findByApplicationNumber(secondNumber).orElseThrow())));
        Admin admin = new Admin();
        admin.setEmail("cancelled-match-admin@example.test");
        admin.setFirstName("Admin");
        admin.setLastName("Test");
        admin.setPasswordHash("test");
        admin = admins.saveAndFlush(admin);
        assertTrue(pendingPair(admin.getId(), firstNumber, secondNumber).isEmpty());
        Long adminId = admin.getId();
        assertEquals(org.springframework.http.HttpStatus.CONFLICT, assertThrows(ResponseStatusException.class,
                () -> reviewService.review(adminId, secondNumber, firstNumber, true)).getStatusCode());

        changeStatus(secondNumber, ApplicationStatus.Cancelled);
        assertEquals(201, submit(request("Cancelled Match", IdentityDocumentType.PASSPORT,
                "CANCELMATCH3", "HK"), third.getId()));
        assertFalse(applications.findByApplicationNumber(applicationNumberFor(third))
                .orElseThrow().isManualIdentityReviewRequired());
    }

    @Test
    void duplicateListUsesFlaggedPendingApplicationRegardlessOfIdOrder() {
        Member first = member("direction-first");
        Member second = member("direction-second");
        assertEquals(201, submit(request("Direction Applicant", IdentityDocumentType.PASSPORT,
                "DIRECTION1", "HK"), first.getId()));
        assertEquals(201, submit(request("Direction Applicant", IdentityDocumentType.PASSPORT,
                "DIRECTION2", "HK"), second.getId()));
        Application a = applications.findByApplicationNumber(applicationNumberFor(first)).orElseThrow();
        Application b = applications.findByApplicationNumber(applicationNumberFor(second)).orElseThrow();
        Application flagged = a.getId() < b.getId() ? a : b;
        Application candidate = flagged == a ? b : a;
        flagged.setManualIdentityReviewRequired(true);
        candidate.setManualIdentityReviewRequired(false);
        applications.saveAllAndFlush(List.of(flagged, candidate));
        Admin admin = new Admin();
        admin.setEmail("direction-admin@example.test");
        admin.setFirstName("Admin");
        admin.setLastName("Test");
        admin.setPasswordHash("test");
        admin = admins.saveAndFlush(admin);

        var matches = pendingPair(admin.getId(), flagged.getApplicationNumber(), candidate.getApplicationNumber());
        assertEquals(1, matches.size());
        assertEquals(flagged.getApplicationNumber(), matches.getFirst().flagged().applicationNumber());
        changeStatus(flagged.getApplicationNumber(), ApplicationStatus.Success);
        assertTrue(pendingPair(admin.getId(), flagged.getApplicationNumber(), candidate.getApplicationNumber()).isEmpty());
    }

    @Test
    void concurrentOfferResponsesCannotOverwriteEachOther() throws Exception {
        Member owner = member("concurrent-offer");
        assertEquals(201, submit(request("Concurrent Offer", IdentityDocumentType.PASSPORT,
                "CONCURRENTOFFER", "HK"), owner.getId()));
        String number = applicationNumberFor(owner);
        Application application = applications.findByApplicationNumber(number).orElseThrow();
        application.setStatus(ApplicationStatus.Success);
        application.setResultReleasedAt(LocalDateTime.now(ZoneId.of("Asia/Hong_Kong")));
        application.setAcceptanceDeadline(application.getResultReleasedAt().plusHours(168));
        applications.saveAndFlush(application);
        var acceptConfirmation = results.offer(owner.getId(), number, OfferDecisionRequest.Decision.Accept);
        var rejectConfirmation = results.offer(owner.getId(), number, OfferDecisionRequest.Decision.Reject);
        var executor = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            var tasks = List.of(OfferDecisionRequest.Decision.Accept, OfferDecisionRequest.Decision.Reject)
                    .stream().map(decision -> executor.submit(() -> {
                        start.await();
                        try {
                            return results.respondToOffer(owner.getId(), number, decision,
                                    decision == OfferDecisionRequest.Decision.Accept ? acceptConfirmation : rejectConfirmation) ? 204 : 409;
                        } catch (ResponseStatusException exception) {
                            return exception.getStatusCode().value();
                        }
                    })).toList();
            start.countDown();
            var codes = List.of(tasks.get(0).get(10, TimeUnit.SECONDS), tasks.get(1).get(10, TimeUnit.SECONDS));
            assertEquals(1, codes.stream().filter(code -> code == 204).count());
            assertEquals(1, codes.stream().filter(code -> code == 409).count());
            assertTrue(List.of(ApplicationStatus.Accepted, ApplicationStatus.Rejected).contains(
                    applications.findByApplicationNumber(number).orElseThrow().getStatus()));
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void concurrentResultAssignmentsCannotExceedLastSeat() throws Exception {
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Hong_Kong"));
        AcademicYear year = new AcademicYear();
        year.setName("last-seat-test-year");
        year.setCreationDate(today);
        year.setStatus(AcademicYearStatus.ARCHIVED);
        year = years.saveAndFlush(year);
        ProgrammeLevel level = new ProgrammeLevel();
        level.setAcademicYear(year);
        level.setLevelCode(LevelCode.S1);
        level.setProgrammeName("Last Seat");
        level.setSeatQuota(1);
        level.setApplicationStartDate(today.minusDays(2));
        level.setApplicationEndDate(today.minusDays(1));
        level = levels.saveAndFlush(level);

        List<String> numbers = List.of("LAST-SEAT-1", "LAST-SEAT-2");
        List<Long> adminIds = new java.util.ArrayList<>();
        for (int i = 0; i < numbers.size(); i++) {
            Member owner = member("last-seat-" + i);
            Applicant applicant = new Applicant();
            applicant.setMember(owner);
            applicant = applicants.saveAndFlush(applicant);
            Application application = new Application();
            application.setApplicationNumber(numbers.get(i));
            application.setMember(owner);
            application.setApplicant(applicant);
            application.setAcademicYear(year);
            application.setProgrammeLevel(level);
            application.setApplicantSnapshot(new ApplicantSnapshot("Last Seat " + i,
                    LocalDate.of(2018, 1, 1), "Unspecified", "12345678", "School",
                    IdentityDocumentType.PASSPORT, "LASTSEAT" + i, "HK"));
            applications.saveAndFlush(application);

            Admin admin = new Admin();
            admin.setEmail("last-seat-admin-" + i + "@example.test");
            admin.setFirstName("Admin");
            admin.setLastName("Test");
            admin.setPasswordHash("test");
            adminIds.add(admins.saveAndFlush(admin).getId());
        }

        var executor = Executors.newFixedThreadPool(2);
        CountDownLatch firstAtNotification = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        CountDownLatch secondStarted = new CountDownLatch(1);
        doAnswer(invocation -> {
            firstAtNotification.countDown();
            if (!releaseFirst.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Timed out waiting to release the first result");
            }
            return null;
        }).when(notifications).notifyApplicationResult(
                argThat(application -> numbers.get(0).equals(application.getApplicationNumber())),
                eq(false), isNull(), eq(false));
        try {
            Future<Boolean> first = executor.submit(() ->
                    results.setResult(adminIds.get(0), numbers.get(0), ApplicationStatus.Success, null));
            assertTrue(firstAtNotification.await(5, TimeUnit.SECONDS));
            Future<Boolean> second = executor.submit(() -> {
                secondStarted.countDown();
                return results.setResult(adminIds.get(1), numbers.get(1), ApplicationStatus.Success, null);
            });
            assertTrue(secondStarted.await(5, TimeUnit.SECONDS));
            assertThrows(TimeoutException.class, () -> second.get(300, TimeUnit.MILLISECONDS));
            releaseFirst.countDown();
            assertTrue(first.get(10, TimeUnit.SECONDS));
            assertFalse(second.get(10, TimeUnit.SECONDS));
            assertEquals(1, applications.countByProgrammeLevel_IdAndStatusIn(level.getId(),
                    List.of(ApplicationStatus.Success, ApplicationStatus.Accepted)));
            assertEquals(1, numbers.stream()
                    .map(number -> applications.findByApplicationNumber(number).orElseThrow().getStatus())
                    .filter(status -> status == ApplicationStatus.Pending).count());
        } finally {
            releaseFirst.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void applicationListsAreFilteredByRoleAndMember() {
        Member first = member("listing-first");
        Member second = member("listing-second");
        assertEquals(201, submit(request("Listing First", IdentityDocumentType.PASSPORT,
                "LISTINGFIRST", "HK"), first.getId()));
        assertEquals(201, submit(request("Listing Second", IdentityDocumentType.PASSPORT,
                "LISTINGSECOND", "HK"), second.getId()));
        String firstNumber = applicationNumberFor(first);
        String secondNumber = applicationNumberFor(second);
        Application firstApplication = applications.findByApplicationNumber(firstNumber).orElseThrow();
        Admin admin = new Admin();
        admin.setEmail("listing-admin@example.test");
        admin.setFirstName("Admin");
        admin.setLastName("Test");
        admin.setPasswordHash("test");
        admin = admins.saveAndFlush(admin);

        var memberPage = results.memberApplications(first.getId(), 0, 100);
        assertTrue(memberPage.stream().anyMatch(item -> item.applicationNumber().equals(firstNumber)));
        assertFalse(memberPage.stream().anyMatch(item -> item.applicationNumber().equals(secondNumber)));
        var listedNumbers = new java.util.HashSet<String>();
        org.springframework.data.domain.Slice<hk.edu.sehs4701.cissas.dto.response.AdminApplication> adminPage;
        int page = 0;
        do {
            adminPage = results.listApplications(admin.getId(), firstApplication.getProgrammeLevel().getId(),
                    ApplicationStatus.Pending, page++, 100);
            adminPage.forEach(item -> listedNumbers.add(item.applicationNumber()));
        } while (adminPage.hasNext());
        assertTrue(listedNumbers.contains(firstNumber));
        assertTrue(listedNumbers.contains(secondNumber));
        assertTrue(results.listApplications(admin.getId(), firstApplication.getProgrammeLevel().getId(),
                ApplicationStatus.Accepted, 0, 100).stream()
                .noneMatch(item -> item.applicationNumber().equals(firstNumber)));

        firstApplication.setStatus(ApplicationStatus.Success);
        firstApplication.setAcceptanceDeadline(LocalDateTime.now(ZoneId.of("Asia/Hong_Kong")).minusMinutes(1));
        applications.saveAndFlush(firstApplication);
        Long levelId = firstApplication.getProgrammeLevel().getId();
        LocalDateTime now = LocalDateTime.now(ZoneId.of("Asia/Hong_Kong"));
        assertTrue(adminListContains(levelId, ApplicationStatus.Rejected, now, firstNumber));
        assertFalse(adminListContains(levelId, ApplicationStatus.Success, now, firstNumber));
        assertTrue(adminListContains(levelId, null, now, firstNumber));
        firstApplication.setStatus(ApplicationStatus.Pending);
        firstApplication.setAcceptanceDeadline(null);
        applications.saveAndFlush(firstApplication);
    }

    private boolean adminListContains(Long levelId, ApplicationStatus status, LocalDateTime now, String number) {
        int page = 0;
        org.springframework.data.domain.Slice<Application> slice;
        do {
            slice = applications.findForAdministration(levelId, status, now,
                    org.springframework.data.domain.PageRequest.of(page++, 100));
            if (slice.stream().anyMatch(item -> item.getApplicationNumber().equals(number))) return true;
        } while (slice.hasNext());
        return false;
    }

    @Test
    void expiredVerificationRecoveryDoesNotFailLiveOrAlreadySentEmails() throws Exception {
        Member owner = member("verification-recovery");
        EmailLog fresh = verificationLog(owner, LocalDateTime.now());
        EmailLog interrupted = verificationLog(owner, LocalDateTime.now().minusMinutes(30));
        EmailLog sending = verificationLog(owner, LocalDateTime.now().minusMinutes(30));
        CountDownLatch locked = new CountDownLatch(1);
        CountDownLatch finish = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            Future<?> send = pool.submit(() -> transactions.executeWithoutResult(status -> {
                EmailLog inFlight = emailLogs.findLockedById(sending.getId()).orElseThrow();
                locked.countDown();
                try {
                    if (!finish.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("Timed out");
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(ex);
                }
                inFlight.setStatus(EmailDeliveryStatus.SENT);
            }));
            assertTrue(locked.await(10, TimeUnit.SECONDS));
            Future<Integer> cleanup = pool.submit(() -> transactions.execute(status ->
                    emailLogs.failExpiredPendingEmailsByTemplateType(EmailTemplateType.OTC_VERIFICATION,
                            EmailDeliveryStatus.PENDING, EmailDeliveryStatus.FAILED,
                            LocalDateTime.now().minusMinutes(15), "expired")));
            try {
                assertThrows(TimeoutException.class, () -> cleanup.get(300, TimeUnit.MILLISECONDS));
            } finally {
                finish.countDown();
            }
            send.get(10, TimeUnit.SECONDS);
            assertTrue(cleanup.get(10, TimeUnit.SECONDS) >= 1);
        }
        assertEquals(EmailDeliveryStatus.PENDING, emailLogs.findById(fresh.getId()).orElseThrow().getStatus());
        assertEquals(EmailDeliveryStatus.FAILED, emailLogs.findById(interrupted.getId()).orElseThrow().getStatus());
        assertEquals(EmailDeliveryStatus.SENT, emailLogs.findById(sending.getId()).orElseThrow().getStatus());
    }

    private EmailLog verificationLog(Member owner, LocalDateTime createdAt) {
        EmailLog email = new EmailLog();
        email.setMember(owner);
        email.setRecipientEmail(owner.getEmail());
        email.setTemplateType(EmailTemplateType.OTC_VERIFICATION);
        email.setActionRequired("Verify email");
        email.setSubject("Verification");
        email.setBody("[Verification code email body redacted]");
        email.setCreatedAt(createdAt);
        return emailLogs.saveAndFlush(email);
    }

    @Test
    void failedDuplicateAlertsAreListedAndPagedIndependently() {
        Member owner = member("alert-recovery");
        EmailLog first = verificationLog(owner, LocalDateTime.now());
        EmailLog second = verificationLog(owner, LocalDateTime.now());
        transactions.executeWithoutResult(status -> {
            for (Long id : List.of(first.getId(), second.getId())) {
                EmailLog alert = emailLogs.findById(id).orElseThrow();
                alert.setTemplateType(EmailTemplateType.APPLICATION_DUPLICATE_ALERT);
                alert.setStatus(EmailDeliveryStatus.FAILED);
            }
        });
        var page = emailLogs.findAllByStatusInAndTemplateTypeAndIdGreaterThanOrderByIdAsc(
                EmailSendAttemptService.UNSENT, EmailTemplateType.APPLICATION_DUPLICATE_ALERT,
                first.getId() - 1, org.springframework.data.domain.PageRequest.of(0, 1));
        assertEquals(first.getId(), page.getContent().getFirst().getId());
        assertTrue(page.hasNext());
        var next = emailLogs.findAllByStatusInAndTemplateTypeAndIdGreaterThanOrderByIdAsc(
                EmailSendAttemptService.UNSENT, EmailTemplateType.APPLICATION_DUPLICATE_ALERT,
                first.getId(), org.springframework.data.domain.PageRequest.of(0, 1));
        assertEquals(second.getId(), next.getContent().getFirst().getId());
        assertTrue(emails.failedApplicationEmails(0).stream().anyMatch(alert -> alert.id().equals(first.getId())));
    }

    @Test
    void skippingFailedEmailRollsBackWithoutLaterEmailAndRecordsAuditWhenLaterEmailExists() {
        Member owner = member("skip-email");
        assertEquals(201, submit(request("Skip Email", IdentityDocumentType.PASSPORT,
                "SKIPEMAIL1", "HK"), owner.getId()));
        Application application = applications.findByApplicationNumber(applicationNumberFor(owner)).orElseThrow();
        EmailLog first = new EmailLog();
        first.setMember(owner);
        first.setApplication(application);
        first.setApplicationNumber(application.getApplicationNumber());
        first.setRecipientEmail(owner.getEmail());
        first.setTemplateType(EmailTemplateType.RESULT_ANNOUNCEMENT);
        first.setActionRequired("Review result");
        first.setSubject("Result");
        first.setBody("Result");
        first.setStatus(EmailDeliveryStatus.FAILED);
        first = emailLogs.saveAndFlush(first);
        Long firstId = first.getId();

        assertThrows(IllegalStateException.class, () -> emails.skipFailedApplicationEmail(firstId, 42L, "Unavailable"));
        assertEquals(EmailDeliveryStatus.FAILED, emailLogs.findById(firstId).orElseThrow().getStatus());

        EmailLog next = new EmailLog();
        next.setMember(owner);
        next.setApplication(application);
        next.setApplicationNumber(application.getApplicationNumber());
        next.setRecipientEmail(owner.getEmail());
        next.setTemplateType(EmailTemplateType.CORRECTED_RESULT_ANNOUNCEMENT);
        next.setActionRequired("Review result");
        next.setSubject("Corrected result");
        next.setBody("Corrected result");
        next = emailLogs.saveAndFlush(next);
        Long nextId = next.getId();
        var queue = emailLogs.findUnsentApplicationEmailHeads(
                EmailSendAttemptService.UNSENT, EmailSendAttemptService.APPLICATION_RESULTS,
                firstId - 1, org.springframework.data.domain.PageRequest.of(0, 100));
        assertTrue(queue.getContent().contains(firstId));
        assertFalse(queue.getContent().contains(nextId));
        emails.skipFailedApplicationEmail(firstId, 42L, "Unavailable");
        EmailLog skipped = emailLogs.findById(firstId).orElseThrow();
        assertEquals(EmailDeliveryStatus.SKIPPED, skipped.getStatus());
        assertEquals(42L, skipped.getSkippedByAdminId());
        assertEquals("Unavailable", skipped.getSkipReason());
        assertNotNull(skipped.getSkippedAt());
        assertThrows(IllegalStateException.class, () -> emails.manualResend(firstId));
        var pendingQueue = emailLogs.findUnsentApplicationEmailHeads(
                EmailSendAttemptService.UNSENT, EmailSendAttemptService.APPLICATION_RESULTS,
                firstId - 1, org.springframework.data.domain.PageRequest.of(0, 100));
        assertFalse(pendingQueue.getContent().contains(firstId));
        assertTrue(pendingQueue.getContent().contains(nextId));
    }

    @Test
    void expiredOfferIsCommittedWhenQuotaUpdateIsRejected() {
        Member expiredOwner = member("quota-expired");
        Member acceptedOwner = member("quota-accepted");
        assertEquals(201, submit(request("Quota Expired", IdentityDocumentType.PASSPORT,
                "QUOTAEXPIRED", "HK"), expiredOwner.getId()));
        assertEquals(201, submit(request("Quota Accepted", IdentityDocumentType.PASSPORT,
                "QUOTAACCEPTED", "HK"), acceptedOwner.getId()));
        Application expired = applications.findByApplicationNumber(applicationNumberFor(expiredOwner)).orElseThrow();
        Application accepted = applications.findByApplicationNumber(applicationNumberFor(acceptedOwner)).orElseThrow();
        expired.setStatus(ApplicationStatus.Success);
        expired.setAcceptanceDeadline(LocalDateTime.now(ZoneId.of("Asia/Hong_Kong")).minusMinutes(1));
        accepted.setStatus(ApplicationStatus.Accepted);
        applications.saveAllAndFlush(List.of(expired, accepted));
        ProgrammeLevel level = levels.findById(expired.getProgrammeLevel().getId()).orElseThrow();

        assertThrows(ResponseStatusException.class, () -> programmeLevels.update(
                level.getAcademicYear().getId(), level.getId(), level.getProgrammeName(), 0,
                level.getApplicationStartDate(), level.getApplicationEndDate()));

        assertEquals(ApplicationStatus.Rejected, applications.findById(expired.getId()).orElseThrow().getStatus());
        assertEquals(ApplicationStatus.Accepted, applications.findById(accepted.getId()).orElseThrow().getStatus());
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
        var mvc = MockMvcBuilders.standaloneSetup(new AdmissionsApplicationController(admissions,
                mock(ApplicationResultService.class))).build();
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
    void adminCanConfirmDuplicateAndCancellationIsRecorded() {
        Member first = member("manual1");
        Member second = member("manual2");
        ApplicationSubmissionRequest one = request("Original Name", IdentityDocumentType.PASSPORT, " MANUAL1 ", "HK");
        ApplicationSubmissionRequest two = request("Original Name", IdentityDocumentType.PASSPORT, "MANUAL2", "HK");
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
        assertEquals("MANUAL2", reviewService.evidence(admin.getId(), secondNumber).documentNumber());
        assertTrue(applications.findByApplicationNumber(secondNumber).orElseThrow().isManualIdentityReviewRequired());
        reviewService.review(admin.getId(), secondNumber, firstNumber, true);
        assertEquals(ApplicationStatus.Cancelled,
                applications.findByApplicationNumber(secondNumber).orElseThrow().getStatus());
        verify(notifications).notifyApplicationCancelled(argThat(application ->
                application.getApplicationNumber().equals(secondNumber)));
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
        verify(notifications, never()).notifyAdminOfPossibleDuplicate(any(), any(), anyString());

        assertEquals(201, submit(request("Resolved Applicant", IdentityDocumentType.HKID, "H123456(2)", null),
                otherMember.getId()));
        changeStatus(resubmittedNumber, ApplicationStatus.Cancelled);
        clearInvocations(notifications);
        assertEquals(201, submit(passport, passportMember.getId()));
        assertTrue(applications.findByApplicationNumber(applicationNumberFor(passportMember))
                .orElseThrow().isManualIdentityReviewRequired());
        verify(notifications).notifyAdminOfPossibleDuplicate(any(), any(), anyString());
    }

    @Test
    void duplicateDecisionCancelsOnlyTheFlaggedApplicationAndOtherMatchesRemainReviewable() {
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
        reviewService.review(admin.getId(), linkedNumber, firstNumber, true);
        assertEquals(ApplicationStatus.Cancelled,
                applications.findByApplicationNumber(linkedNumber).orElseThrow().getStatus());
        reviewService.review(admin.getId(), differentNumber, firstNumber, false);
        changeStatus(firstNumber, ApplicationStatus.Cancelled);
        clearInvocations(notifications);
        assertEquals(201, submit(firstRequest, first.getId()));
        String secondNumber = applicationNumberFor(first);
        assertFalse(applications.findByApplicationNumber(secondNumber).orElseThrow().isManualIdentityReviewRequired());
        verify(notifications, never()).notifyAdminOfPossibleDuplicate(any(), any(), anyString());

        assertEquals(201, submit(request("Group Applicant", IdentityDocumentType.HKID, "V123456(0)", null), unresolved.getId()));
        String unresolvedNumber = applicationNumberFor(unresolved);
        changeStatus(secondNumber, ApplicationStatus.Cancelled);
        clearInvocations(notifications);
        assertEquals(201, submit(firstRequest, first.getId()));
        String thirdNumber = applicationNumberFor(first);
        assertTrue(applications.findByApplicationNumber(thirdNumber).orElseThrow().isManualIdentityReviewRequired());
        verify(notifications).notifyAdminOfPossibleDuplicate(any(), any(), anyString());

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
    void duplicateFlagRemainsUntilEveryPossibleMatchIsResolved() {
        Member first = member("reopen-first");
        Member linked = member("reopen-linked");
        Member different = member("reopen-different");
        var firstRequest = request("Reopen Applicant", IdentityDocumentType.PASSPORT, "REOPEN1", "HK");
        assertEquals(201, submit(firstRequest, first.getId()));
        assertEquals(201, submit(request("Reopen Applicant", IdentityDocumentType.PASSPORT, "REOPEN2", "HK"), linked.getId()));
        assertEquals(201, submit(request("Reopen Applicant", IdentityDocumentType.PASSPORT, "REOPEN3", "HK"), different.getId()));
        String firstNumber = applicationNumberFor(first);
        String linkedNumber = applicationNumberFor(linked);
        String differentNumber = applicationNumberFor(different);
        Admin admin = new Admin();
        admin.setEmail("reopen-admin@example.test");
        admin.setFirstName("Admin");
        admin.setLastName("Reopen");
        admin.setPasswordHash("test");
        admin = admins.saveAndFlush(admin);

        assertTrue(applications.findByApplicationNumber(differentNumber).orElseThrow().isManualIdentityReviewRequired());
        reviewService.review(admin.getId(), differentNumber, firstNumber, false);
        assertTrue(applications.findByApplicationNumber(differentNumber).orElseThrow().isManualIdentityReviewRequired());
        reviewService.review(admin.getId(), differentNumber, linkedNumber, false);
        assertFalse(applications.findByApplicationNumber(differentNumber).orElseThrow().isManualIdentityReviewRequired());
        assertTrue(pendingPair(admin.getId(), differentNumber, firstNumber).isEmpty());
        assertTrue(pendingPair(admin.getId(), differentNumber, linkedNumber).isEmpty());
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
        assertEquals("REVOKED-READ", applicationController.duplicateEvidence(number, request).getBody().documentNumber());

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
                                    () -> applicationController.duplicateEvidence(number, request)).getStatusCode());
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

        boolean hkidWasCancelled = pair.flagged().documentType() == IdentityDocumentType.HKID;
        Member cancelledOwner = hkidWasCancelled ? first : second;
        Member originalOwner = hkidWasCancelled ? second : first;
        ApplicationSubmissionRequest cancelledRequest = hkidWasCancelled ? hkid : passport;
        ApplicationSubmissionRequest originalRequest = hkidWasCancelled ? passport : hkid;
        assertEquals(ApplicationStatus.Cancelled,
                applications.findByApplicationNumber(pair.flagged().applicationNumber()).orElseThrow().getStatus());
        assertEquals(409, submit(originalRequest, originalOwner.getId()));
        assertEquals(201, submit(cancelledRequest, cancelledOwner.getId()));
        assertTrue(applications.findByApplicationNumber(applicationNumberFor(cancelledOwner))
                .orElseThrow().isManualIdentityReviewRequired());
    }

    @Test
    void historyCursorReturnsEveryDecisionWhenNewDecisionIsAdded() {
        Member first = member("history1");
        Member second = member("history2");
        Member third = member("history3");
        Member fourth = member("history4");
        assertEquals(201, submit(request("History Applicant", IdentityDocumentType.PASSPORT, "HISTORY1", "HK"), first.getId()));
        assertEquals(201, submit(request("History Applicant", IdentityDocumentType.PASSPORT, "HISTORY2", "HK"), second.getId()));
        assertEquals(201, submit(request("History Applicant", IdentityDocumentType.PASSPORT, "HISTORY3", "HK"), third.getId()));
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
        reviewService.review(adminId, three, one, false);
        reviewService.review(adminId, three, two, false);
        var firstPage = reviewService.history(adminId, null, 1);
        assertEquals(three, firstPage.items().getFirst().flaggedApplicationNumber());
        assertEquals(two, firstPage.items().getFirst().candidateApplicationNumber());
        assertTrue(firstPage.items().getFirst().current());
        assertNotNull(firstPage.nextCursor());
        assertEquals(201, submit(request("History Applicant", IdentityDocumentType.PASSPORT, "HISTORY4", "HK"), fourth.getId()));
        String four = applicationNumberFor(fourth);
        reviewService.review(adminId, four, one, false);
        var secondPage = reviewService.history(adminId, firstPage.nextCursor(), 1);
        assertEquals(three, secondPage.items().getFirst().flaggedApplicationNumber());
        assertEquals(one, secondPage.items().getFirst().candidateApplicationNumber());
        assertTrue(secondPage.items().getFirst().current());
        assertNotEquals(firstPage.items().getFirst(), secondPage.items().getFirst());
        assertEquals(four, reviewService.history(adminId, null, 1).items().getFirst().flaggedApplicationNumber());
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
