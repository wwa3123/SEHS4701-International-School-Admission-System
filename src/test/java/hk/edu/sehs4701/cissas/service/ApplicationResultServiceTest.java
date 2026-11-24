package hk.edu.sehs4701.cissas.service;

import hk.edu.sehs4701.cissas.dto.request.OfferDecisionRequest;
import hk.edu.sehs4701.cissas.entity.AcademicYear;
import hk.edu.sehs4701.cissas.entity.Admin;
import hk.edu.sehs4701.cissas.entity.Application;
import hk.edu.sehs4701.cissas.entity.Member;
import hk.edu.sehs4701.cissas.entity.ProgrammeLevel;
import hk.edu.sehs4701.cissas.entity.StatusHistory;
import hk.edu.sehs4701.cissas.enums.AcademicYearStatus;
import hk.edu.sehs4701.cissas.enums.ActorRole;
import hk.edu.sehs4701.cissas.enums.ApplicationStatus;
import hk.edu.sehs4701.cissas.enums.IdentityDocumentType;
import hk.edu.sehs4701.cissas.enums.LevelCode;
import hk.edu.sehs4701.cissas.repository.AdminRepository;
import hk.edu.sehs4701.cissas.repository.ApplicationRepository;
import hk.edu.sehs4701.cissas.repository.ProgrammeLevelRepository;
import hk.edu.sehs4701.cissas.repository.StatusHistoryRepository;
import hk.edu.sehs4701.cissas.repository.IdentityReviewLockRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.SliceImpl;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ApplicationResultServiceTest {
    private final ApplicationRepository applications = mock(ApplicationRepository.class);
    private final ProgrammeLevelRepository levels = mock(ProgrammeLevelRepository.class);
    private final StatusHistoryRepository history = mock(StatusHistoryRepository.class);
    private final AdminRepository admins = mock(AdminRepository.class);
    private final NotificationService notifications = mock(NotificationService.class);
    private final IdentityReviewService duplicates = mock(IdentityReviewService.class);
    private final IdentityReviewLockRepository duplicateLock = mock(IdentityReviewLockRepository.class);
    private final ApplicationResultService service =
            new ApplicationResultService(applications, levels, history, admins, notifications, duplicates, duplicateLock);
    private final Admin admin = new Admin();
    private final Application application = new Application();
    private final ProgrammeLevel level = new ProgrammeLevel();
    private final Member member = new Member();

    @BeforeEach
    void setUp() {
        admin.setId(42L);
        admin.setRole(ActorRole.ROLE_ADMIN);
        member.setId(5L);
        application.setId(1L);
        application.setApplicationNumber("APP-1");
        application.setStatus(ApplicationStatus.Pending);
        application.setMember(member);
        application.setSubmittedAt(LocalDateTime.of(2026, 9, 1, 10, 0));
        application.setDeclarationsAcceptedAt(LocalDateTime.of(2026, 9, 1, 9, 59));
        application.setApplicantSnapshot(new hk.edu.sehs4701.cissas.entity.ApplicantSnapshot(
                "Example", LocalDate.of(2018, 1, 1), "Unspecified", "123", "School",
                IdentityDocumentType.HKID, "123456", null));
        level.setId(7L);
        level.setLevelCode(LevelCode.S1);
        level.setProgrammeName("Primary");
        level.setApplicationEndDate(LocalDate.of(2020, 9, 30));
        level.setSeatQuota(2);
        application.setProgrammeLevel(level);
        AcademicYear year = new AcademicYear();
        year.setId(8L);
        year.setName("2026/27");
        year.setStatus(AcademicYearStatus.ACTIVE);
        application.setAcademicYear(year);
        when(admins.findById(42L)).thenReturn(Optional.of(admin));
        when(applications.findByApplicationNumber("APP-1")).thenReturn(Optional.of(application));
        when(applications.findProgrammeLevelId("APP-1")).thenReturn(Optional.of(7L));
        when(applications.findProgrammeLevelIdForMember("APP-1", 5L)).thenReturn(Optional.of(7L));
        when(applications.findLockedByApplicationNumber("APP-1")).thenReturn(Optional.of(application));
        when(applications.findLockedByApplicationNumberAndMember_Id("APP-1", 5L)).thenReturn(Optional.of(application));
        when(levels.findLockedById(7L)).thenReturn(Optional.of(level));
    }

    @Test
    void pendingCanReceiveAllThreeResultsAndSendsAnEmail() {
        for (ApplicationStatus result : List.of(
                ApplicationStatus.Success, ApplicationStatus.Wait, ApplicationStatus.Fail)) {
            application.setStatus(ApplicationStatus.Pending);
            service.setResult(42L, "APP-1", result, null);
            assertEquals(result, application.getStatus());
            assertNotNull(application.getResultReleasedAt());
            if (result == ApplicationStatus.Success) {
                assertEquals(application.getResultReleasedAt().plusHours(168), application.getAcceptanceDeadline());
            } else {
                assertNull(application.getAcceptanceDeadline());
            }
        }
        verify(history, times(3)).save(any(StatusHistory.class));
        verify(notifications, times(3)).notifyApplicationResult(any(), eq(false), eq(false));
    }

    @Test
    void availableResultChoicesFollowRoleStatusAndOfferDeadline() {
        assertEquals(List.of(ApplicationStatus.Success, ApplicationStatus.Wait, ApplicationStatus.Fail),
                service.availableResults(42L, "APP-1"));

        application.setStatus(ApplicationStatus.Wait);
        assertEquals(List.of(ApplicationStatus.Success), service.availableResults(42L, "APP-1"));
        admin.setRole(ActorRole.ROLE_SUPER_ADMIN);
        assertEquals(List.of(ApplicationStatus.Success, ApplicationStatus.Fail), service.availableResults(42L, "APP-1"));

        application.setStatus(ApplicationStatus.Success);
        application.setAcceptanceDeadline(LocalDateTime.now(ZoneId.of("Asia/Hong_Kong")).plusHours(1));
        assertEquals(List.of(ApplicationStatus.Wait, ApplicationStatus.Fail), service.availableResults(42L, "APP-1"));
        application.setRespondedAt(LocalDateTime.now(ZoneId.of("Asia/Hong_Kong")));
        assertTrue(service.availableResults(42L, "APP-1").isEmpty());

        application.setRespondedAt(null);
        application.setStatus(ApplicationStatus.Accepted);
        assertTrue(service.availableResults(42L, "APP-1").isEmpty());
    }

    @Test
    void refusesToChangeApplicationResultForArchivedYear() {
        application.getAcademicYear().setStatus(AcademicYearStatus.ARCHIVED);

        assertEquals(HttpStatus.CONFLICT, assertThrows(ResponseStatusException.class,
                () -> service.setResult(42L, "APP-1", ApplicationStatus.Success, null)).getStatusCode());
        verify(applications, never()).save(any());
        verify(history, never()).save(any());
    }

    @Test
    void waitPromotionNeedsAFreeSeatAndIsRecorded() {
        application.setStatus(ApplicationStatus.Wait);
        when(applications.countByProgrammeLevel_IdAndStatusIn(7L,
                List.of(ApplicationStatus.Success, ApplicationStatus.Accepted))).thenReturn(1L);

        service.setResult(42L, "APP-1", ApplicationStatus.Success, null);

        ArgumentCaptor<StatusHistory> saved = ArgumentCaptor.forClass(StatusHistory.class);
        verify(history).save(saved.capture());
        assertEquals(ApplicationStatus.Wait, saved.getValue().getFromStatus());
        assertEquals(ApplicationStatus.Success, saved.getValue().getToStatus());
        verify(notifications).notifyApplicationResult(application, false, true);
    }

    @Test
    void waitPromotionIsBlockedAtQuota() {
        application.setStatus(ApplicationStatus.Wait);
        when(applications.countByProgrammeLevel_IdAndStatusIn(7L,
                List.of(ApplicationStatus.Success, ApplicationStatus.Accepted))).thenReturn(2L);
        assertFalse(service.setResult(42L, "APP-1", ApplicationStatus.Success, null));
        verify(history, never()).save(any());
    }

    @Test
    void onlySuperAdminCanCorrectAnUnansweredResultWithAReason() {
        application.setStatus(ApplicationStatus.Fail);
        application.setResultReleasedAt(LocalDateTime.now().minusHours(1));
        assertEquals(HttpStatus.FORBIDDEN, assertThrows(ResponseStatusException.class,
                () -> service.setResult(42L, "APP-1", ApplicationStatus.Success, "Correction")).getStatusCode());

        admin.setRole(ActorRole.ROLE_SUPER_ADMIN);
        assertEquals(HttpStatus.BAD_REQUEST, assertThrows(ResponseStatusException.class,
                () -> service.setResult(42L, "APP-1", ApplicationStatus.Success, "  ")).getStatusCode());
        when(applications.countByProgrammeLevel_IdAndStatusIn(7L,
                List.of(ApplicationStatus.Success, ApplicationStatus.Accepted))).thenReturn(0L);
        service.setResult(42L, "APP-1", ApplicationStatus.Success, "Corrected identity A123456(3)");

        verify(history).save(argThat(entry -> entry.getFromStatus() == ApplicationStatus.Fail
                && entry.getToStatus() == ApplicationStatus.Success
                && "Corrected identity A123456(3)".equals(entry.getRemarks())));
        verify(notifications).notifyApplicationResult(application, true, false);
    }

    @Test
    void correctionIsBlockedAfterMemberRespondsAndFinalStatusesNeverChange() {
        admin.setRole(ActorRole.ROLE_SUPER_ADMIN);
        application.setStatus(ApplicationStatus.Success);
        application.setRespondedAt(LocalDateTime.now().minusMinutes(1));
        assertEquals(HttpStatus.CONFLICT, assertThrows(ResponseStatusException.class,
                () -> service.setResult(42L, "APP-1", ApplicationStatus.Fail, "reason")).getStatusCode());
        application.setStatus(ApplicationStatus.Accepted);
        assertEquals(HttpStatus.CONFLICT, assertThrows(ResponseStatusException.class,
                () -> service.setResult(42L, "APP-1", ApplicationStatus.Fail, "reason")).getStatusCode());
    }

    @Test
    void expiredOfferIsRejectedInsteadOfCorrected() {
        admin.setRole(ActorRole.ROLE_SUPER_ADMIN);
        application.setStatus(ApplicationStatus.Success);
        application.setAcceptanceDeadline(LocalDateTime.now(ZoneId.of("Asia/Hong_Kong")).minusSeconds(1));

        assertFalse(service.setResult(42L, "APP-1", ApplicationStatus.Fail, "Correction"));
        assertEquals(ApplicationStatus.Rejected, application.getStatus());
        verify(notifications, never()).notifyApplicationResult(any(), anyBoolean(), anyBoolean());
    }

    @Test
    void cannotReleaseInitialResultBeforeClosingDate() {
        level.setApplicationEndDate(LocalDate.now(ZoneId.of("Asia/Hong_Kong")));
        assertEquals(HttpStatus.CONFLICT, assertThrows(ResponseStatusException.class,
                () -> service.setResult(42L, "APP-1", ApplicationStatus.Wait, null)).getStatusCode());
    }

    @Test
    void cannotPromoteOrCorrectBeforeRevisedClosingDate() {
        level.setApplicationEndDate(LocalDate.now(ZoneId.of("Asia/Hong_Kong")).plusDays(1));
        application.setStatus(ApplicationStatus.Wait);
        assertEquals(HttpStatus.CONFLICT, assertThrows(ResponseStatusException.class,
                () -> service.setResult(42L, "APP-1", ApplicationStatus.Success, null)).getStatusCode());
        admin.setRole(ActorRole.ROLE_SUPER_ADMIN);
        application.setStatus(ApplicationStatus.Fail);
        assertEquals(HttpStatus.CONFLICT, assertThrows(ResponseStatusException.class,
                () -> service.setResult(42L, "APP-1", ApplicationStatus.Wait, "Correction")).getStatusCode());
    }

    @Test
    void expiresOverdueOffersBeforeCheckingAvailableSeats() {
        application.setStatus(ApplicationStatus.Wait);
        Application expired = new Application();
        expired.setAcademicYear(application.getAcademicYear());
        expired.setStatus(ApplicationStatus.Success);
        when(applications.findAllByProgrammeLevel_IdAndStatusAndAcceptanceDeadlineLessThanEqual(
                eq(7L), eq(ApplicationStatus.Success), any())).thenReturn(List.of(expired));

        service.setResult(42L, "APP-1", ApplicationStatus.Success, null);

        assertEquals(ApplicationStatus.Rejected, expired.getStatus());
        var order = inOrder(applications);
        order.verify(applications).findAllByProgrammeLevel_IdAndStatusAndAcceptanceDeadlineLessThanEqual(
                eq(7L), eq(ApplicationStatus.Success), any());
        order.verify(applications).save(expired);
        order.verify(applications).countByProgrammeLevel_IdAndStatusIn(eq(7L), anyList());
    }

    @Test
    void readingAnExpiredOfferPersistsRejection() {
        application.setStatus(ApplicationStatus.Success);
        application.setAcceptanceDeadline(LocalDateTime.now(ZoneId.of("Asia/Hong_Kong")).minusSeconds(1));

        assertNull(service.offer(5L, "APP-1", OfferDecisionRequest.Decision.Accept));

        assertEquals(ApplicationStatus.Rejected, application.getStatus());
        verify(history).save(argThat(entry -> entry.getChangedByRole() == ActorRole.ROLE_SYSTEM));
    }

    @Test
    void cancelledOnlyMatchesNoLongerBlockResult() {
        application.setManualIdentityReviewRequired(true);
        when(duplicates.hasPossibleDuplicate(application)).thenReturn(false);

        service.setResult(42L, "APP-1", ApplicationStatus.Wait, null);

        assertFalse(application.isManualIdentityReviewRequired());
        assertEquals(ApplicationStatus.Wait, application.getStatus());
    }

    @Test
    void activeDuplicateStillBlocksResult() {
        application.setManualIdentityReviewRequired(true);
        when(duplicates.hasPossibleDuplicate(application)).thenReturn(true);

        assertEquals(HttpStatus.CONFLICT, assertThrows(ResponseStatusException.class,
                () -> service.setResult(42L, "APP-1", ApplicationStatus.Wait, null)).getStatusCode());
        assertTrue(application.isManualIdentityReviewRequired());
        verify(applications, never()).save(any());
    }

    @Test
    void memberResponseUpdatesOfferOnceAndExpiresAtDeadline() {
        application.setStatus(ApplicationStatus.Success);
        application.setAcceptanceDeadline(LocalDateTime.now(ZoneId.of("Asia/Hong_Kong")).plusHours(1));
        var confirmation = service.offer(5L, "APP-1", OfferDecisionRequest.Decision.Accept);
        assertTrue(service.respondToOffer(5L, "APP-1", OfferDecisionRequest.Decision.Accept, confirmation));
        assertEquals(ApplicationStatus.Accepted, application.getStatus());
        verify(history).save(argThat(entry -> entry.getToStatus() == ApplicationStatus.Accepted
                && entry.getChangedByRole() == ActorRole.ROLE_MEMBER));
        assertEquals(HttpStatus.CONFLICT, assertThrows(ResponseStatusException.class,
                () -> service.respondToOffer(5L, "APP-1", OfferDecisionRequest.Decision.Reject, confirmation)).getStatusCode());
    }

    @Test
    void confirmationDetailsBelongOnlyToTheApplicantAndShowDeadline() {
        application.setStatus(ApplicationStatus.Success);
        LocalDateTime deadline = LocalDateTime.now(ZoneId.of("Asia/Hong_Kong")).plusHours(168);
        application.setAcceptanceDeadline(deadline);
        var confirmation = service.offer(5L, "APP-1", OfferDecisionRequest.Decision.Accept);
        assertEquals("S1 - IB MYP", confirmation.programmeLevel());
        assertEquals("APP-1", confirmation.applicationNumber());
        assertEquals(deadline, confirmation.acceptanceDeadline());
        assertEquals(HttpStatus.NOT_FOUND, assertThrows(ResponseStatusException.class,
                () -> service.offer(6L, "APP-1", OfferDecisionRequest.Decision.Accept)).getStatusCode());
    }

    @Test
    void confirmationsAndEnquiriesDistinguishEverySchoolLevelEvenWithLegacyProgrammeNames() {
        application.setStatus(ApplicationStatus.Success);
        application.setAcceptanceDeadline(LocalDateTime.now(ZoneId.of("Asia/Hong_Kong")).plusHours(24));
        var expected = List.of("S1 - IB MYP", "S2 - IB MYP", "S3 - IB MYP",
                "S4 - IB MYP", "S5 - IB DP", "S6 - IB DP");
        for (int index = 0; index < LevelCode.values().length; index++) {
            level.setLevelCode(LevelCode.values()[index]);
            assertEquals(expected.get(index),
                    service.offer(5L, "APP-1", OfferDecisionRequest.Decision.Accept).programmeLevel());
            assertEquals(expected.get(index), service.memberResult(5L, "APP-1").programmeLevel());
        }
    }

    @Test
    void memberCanReadWaitAndFailButNotAnotherMembersResult() {
        for (ApplicationStatus status : List.of(ApplicationStatus.Wait, ApplicationStatus.Fail)) {
            application.setStatus(status);
            assertEquals(status, service.memberResult(5L, "APP-1").status());
        }

        assertEquals(HttpStatus.NOT_FOUND, assertThrows(ResponseStatusException.class,
                () -> service.memberResult(6L, "APP-1")).getStatusCode());
    }

    @Test
    void memberResultIncludesSubmittedSnapshotAndOrderedStatusHistory() {
        StatusHistory transition = new StatusHistory();
        transition.setFromStatus(ApplicationStatus.Pending);
        transition.setToStatus(ApplicationStatus.Wait);
        transition.setChangedByRole(ActorRole.ROLE_ADMIN);
        transition.setRemarks("Waiting list");
        transition.setChangedAt(LocalDateTime.of(2026, 10, 1, 12, 0));
        when(history.findAllByApplication_IdOrderByChangedAtAsc(1L)).thenReturn(List.of(transition));

        var result = service.memberResult(5L, "APP-1");

        assertEquals("Example", result.applicantName());
        assertEquals("2026/27", result.academicYear());
        assertEquals("S1 - IB MYP", result.programmeLevel());
        assertEquals("123", result.phone());
        assertEquals(IdentityDocumentType.HKID, result.identityDocumentType());
        assertEquals("123456", result.identityDocumentNumber());
        assertEquals(LocalDateTime.of(2026, 9, 1, 10, 0), result.submittedAt());
        assertEquals(2, result.statusHistory().size());
        assertEquals(ApplicationStatus.Pending, result.statusHistory().getFirst().toStatus());
        assertEquals(ApplicationStatus.Wait, result.statusHistory().get(1).toStatus());
        assertEquals("Waiting list", result.statusHistory().get(1).remarks());
    }

    @Test
    void administratorsCanListAndFilterApplications() {
        when(levels.existsById(7L)).thenReturn(true);
        when(applications.findForAdministration(eq(7L), eq(ApplicationStatus.Pending), any(LocalDateTime.class), eq(PageRequest.of(0, 25))))
                .thenReturn(new SliceImpl<>(List.of(application)));

        var result = service.listApplications(42L, 7L, ApplicationStatus.Pending, 0, 25);

        assertEquals("APP-1", result.getContent().getFirst().applicationNumber());
        assertEquals(7L, result.getContent().getFirst().programmeLevelId());
        assertEquals("S1 - IB MYP", result.getContent().getFirst().programmeLevel());
        assertEquals(ApplicationStatus.Pending, result.getContent().getFirst().status());
        assertEquals(HttpStatus.BAD_REQUEST, assertThrows(ResponseStatusException.class,
                () -> service.listApplications(42L, null, null, 0, 101)).getStatusCode());
    }

    @Test
    void administratorListShowsExpiredSuccessAsRejected() {
        application.setStatus(ApplicationStatus.Success);
        application.setAcceptanceDeadline(LocalDateTime.now(ZoneId.of("Asia/Hong_Kong")).minusSeconds(1));
        when(applications.findForAdministration(isNull(), eq(ApplicationStatus.Rejected),
                any(LocalDateTime.class), eq(PageRequest.of(0, 25))))
                .thenReturn(new SliceImpl<>(List.of(application)));

        var result = service.listApplications(42L, null, ApplicationStatus.Rejected, 0, 25);
        assertEquals(ApplicationStatus.Rejected, result.getContent().getFirst().status());
    }

    @Test
    void effectiveStatusChangesAtDeadlineWithoutChangingStoredStatus() {
        LocalDateTime deadline = LocalDateTime.of(2026, 1, 1, 12, 0);
        application.setStatus(ApplicationStatus.Success);
        application.setAcceptanceDeadline(deadline);
        assertEquals(ApplicationStatus.Success, application.effectiveStatus(deadline.minusNanos(1)));
        assertEquals(ApplicationStatus.Rejected, application.effectiveStatus(deadline));
        assertEquals(ApplicationStatus.Success, application.getStatus());
        application.setStatus(ApplicationStatus.Accepted);
        assertEquals(ApplicationStatus.Accepted, application.effectiveStatus(deadline.plusSeconds(1)));
    }

    @Test
    void rejectsDecisionDifferentFromConfirmedDecision() {
        application.setStatus(ApplicationStatus.Success);
        application.setAcceptanceDeadline(LocalDateTime.now(ZoneId.of("Asia/Hong_Kong")).plusHours(1));
        var confirmation = service.offer(5L, "APP-1", OfferDecisionRequest.Decision.Accept);
        assertEquals(HttpStatus.CONFLICT, assertThrows(ResponseStatusException.class,
                () -> service.respondToOffer(5L, "APP-1", OfferDecisionRequest.Decision.Reject, confirmation))
                .getStatusCode());
        assertEquals(ApplicationStatus.Success, application.getStatus());
    }

    @Test
    void memberApplicationListIsScopedToLoggedInMember() {
        when(applications.findAllByMember_IdOrderByIdAsc(5L, PageRequest.of(0, 25)))
                .thenReturn(new SliceImpl<>(List.of(application)));

        var result = service.memberApplications(5L, 0, 25);

        assertEquals("APP-1", result.getContent().getFirst().applicationNumber());
        assertEquals("S1 - IB MYP", result.getContent().getFirst().programmeLevel());
        verify(applications).findAllByMember_IdOrderByIdAsc(5L, PageRequest.of(0, 25));
    }

    @Test
    void expiredOfferIsRejectedAndSeatIsReleased() {
        application.setStatus(ApplicationStatus.Success);
        application.setAcceptanceDeadline(LocalDateTime.now(ZoneId.of("Asia/Hong_Kong")).minusSeconds(1));
        var confirmation = new hk.edu.sehs4701.cissas.dto.response.OfferConfirmation(
                "Primary", "APP-1", application.getAcceptanceDeadline(), null, OfferDecisionRequest.Decision.Accept, "token");
        assertFalse(service.respondToOffer(5L, "APP-1", OfferDecisionRequest.Decision.Accept, confirmation));
        assertEquals(ApplicationStatus.Rejected, application.getStatus());
        assertNotNull(application.getRespondedAt());
        verify(history).save(argThat(entry -> entry.getToStatus() == ApplicationStatus.Rejected
                && entry.getChangedByRole() == ActorRole.ROLE_SYSTEM));
    }

    @Test
    void changedOfferMustBeReviewedAgainBeforeResponding() {
        application.setStatus(ApplicationStatus.Success);
        application.setAcceptanceDeadline(LocalDateTime.now(ZoneId.of("Asia/Hong_Kong")).plusHours(1));
        var confirmation = service.offer(5L, "APP-1", OfferDecisionRequest.Decision.Accept);
        application.setAcceptanceDeadline(application.getAcceptanceDeadline().plusHours(1));

        assertEquals(HttpStatus.CONFLICT, assertThrows(ResponseStatusException.class,
                () -> service.respondToOffer(5L, "APP-1", OfferDecisionRequest.Decision.Accept, confirmation))
                .getStatusCode());
        assertEquals(ApplicationStatus.Success, application.getStatus());
    }

    @Test
    void expiryJobRejectsAnUnansweredExpiredOffer() {
        application.setStatus(ApplicationStatus.Success);
        application.setAcceptanceDeadline(LocalDateTime.now(ZoneId.of("Asia/Hong_Kong")).minusSeconds(1));
        service.expireOffer("APP-1");
        assertEquals(ApplicationStatus.Rejected, application.getStatus());
        verify(history).save(argThat(entry -> entry.getChangedByRole() == ActorRole.ROLE_SYSTEM
                && entry.getToStatus() == ApplicationStatus.Rejected));
    }

    @Test
    void expiryJobDoesNotChangeAnArchivedApplication() {
        application.getAcademicYear().setStatus(AcademicYearStatus.ARCHIVED);
        application.setStatus(ApplicationStatus.Success);
        application.setAcceptanceDeadline(LocalDateTime.now(ZoneId.of("Asia/Hong_Kong")).minusSeconds(1));

        service.expireOffer("APP-1");

        assertEquals(ApplicationStatus.Success, application.getStatus());
        verify(applications, never()).save(any());
        verify(history, never()).save(any());
        verify(duplicateLock).acquire();
    }

    @Test
    void memberCanReadAnArchivedOfferWithoutExpiringIt() {
        application.getAcademicYear().setStatus(AcademicYearStatus.ARCHIVED);
        application.setStatus(ApplicationStatus.Success);
        application.setAcceptanceDeadline(LocalDateTime.now(ZoneId.of("Asia/Hong_Kong")).minusSeconds(1));

        var result = service.memberResult(5L, "APP-1");

        assertEquals(ApplicationStatus.Success, result.status());
        verify(applications, never()).save(any());
        verify(history, never()).save(any());
    }

    @Test
    void memberCancelsOwnedPendingApplicationBeforeClosingDateAndKeepsAudit() {
        level.setApplicationEndDate(LocalDate.now(ZoneId.of("Asia/Hong_Kong")).plusDays(1));
        application.setManualIdentityReviewRequired(true);

        service.cancelPendingApplication(5L, "APP-1");

        assertEquals(ApplicationStatus.Cancelled, application.getStatus());
        assertFalse(application.isManualIdentityReviewRequired());
        verify(applications).save(application);
        verify(history).save(argThat(entry -> entry.getFromStatus() == ApplicationStatus.Pending
                && entry.getToStatus() == ApplicationStatus.Cancelled
                && entry.getChangedByRole() == ActorRole.ROLE_MEMBER
                && entry.getChangedById() == 5L));
        verify(notifications).notifyMemberApplicationCancelled(application);
        assertThrows(ResponseStatusException.class, () -> service.cancelPendingApplication(5L, "APP-1"));
        verify(notifications, times(1)).notifyMemberApplicationCancelled(application);
    }

    @Test
    void memberCannotCancelAfterClosingDateOrCancelAnotherMembersApplication() {
        level.setApplicationEndDate(LocalDate.now(ZoneId.of("Asia/Hong_Kong")));
        assertEquals(HttpStatus.CONFLICT, assertThrows(ResponseStatusException.class,
                () -> service.cancelPendingApplication(5L, "APP-1")).getStatusCode());
        when(applications.findProgrammeLevelIdForMember("APP-1", 99L)).thenReturn(Optional.empty());
        assertEquals(HttpStatus.NOT_FOUND, assertThrows(ResponseStatusException.class,
                () -> service.cancelPendingApplication(99L, "APP-1")).getStatusCode());
        verify(applications, never()).save(any());
        verify(notifications, never()).notifyMemberApplicationCancelled(any());
    }

    @Test
    void deadlineExtensionRequiresUnexpiredOfferAndPersistsReason() {
        application.setStatus(ApplicationStatus.Success);
        application.setAcceptanceDeadline(LocalDateTime.now(ZoneId.of("Asia/Hong_Kong")).plusHours(1));
        application.setOfferReminderQueuedAt(LocalDateTime.now(ZoneId.of("Asia/Hong_Kong")).minusHours(1));
        LocalDateTime extended = application.getAcceptanceDeadline().plusHours(2);
        assertTrue(service.extendDeadline(42L, "APP-1", extended, "System fault"));
        assertEquals(extended, application.getAcceptanceDeadline());
        assertNull(application.getOfferReminderQueuedAt());
        verify(history).save(argThat(entry -> entry.getFromStatus() == ApplicationStatus.Success
                && entry.getToStatus() == ApplicationStatus.Success && "System fault".equals(entry.getRemarks())));
        verify(notifications).notifyOfferDeadlineExtended(application);
    }

    @Test
    void offerReminderQueuesOnlyOnceForAnUnansweredOfferWithin48Hours() {
        application.setStatus(ApplicationStatus.Success);
        application.setAcceptanceDeadline(LocalDateTime.now(ZoneId.of("Asia/Hong_Kong")).plusHours(24));

        assertTrue(service.sendOfferReminder("APP-1"));
        assertNotNull(application.getOfferReminderQueuedAt());
        assertFalse(service.sendOfferReminder("APP-1"));

        verify(applications, times(1)).save(application);
        verify(notifications, times(1)).notifyOfferReminder(application);
    }

    @Test
    void offerReminderSkipsExpiredRespondedAndArchivedOffers() {
        application.setStatus(ApplicationStatus.Success);
        application.setAcceptanceDeadline(LocalDateTime.now(ZoneId.of("Asia/Hong_Kong")).minusSeconds(1));
        assertFalse(service.sendOfferReminder("APP-1"));

        application.setAcceptanceDeadline(LocalDateTime.now(ZoneId.of("Asia/Hong_Kong")).plusHours(1));
        application.setRespondedAt(LocalDateTime.now(ZoneId.of("Asia/Hong_Kong")));
        assertFalse(service.sendOfferReminder("APP-1"));

        application.setRespondedAt(null);
        application.getAcademicYear().setStatus(AcademicYearStatus.ARCHIVED);
        assertFalse(service.sendOfferReminder("APP-1"));

        verify(applications, never()).save(any());
        verify(notifications, never()).notifyOfferReminder(any());
    }

    @Test
    void expiredOfferIsRejectedWhenExtensionIsRequested() {
        application.setStatus(ApplicationStatus.Success);
        application.setAcceptanceDeadline(LocalDateTime.now(ZoneId.of("Asia/Hong_Kong")).minusSeconds(1));

        assertFalse(service.extendDeadline(42L, "APP-1", application.getAcceptanceDeadline().plusDays(1), "System fault"));
        assertEquals(ApplicationStatus.Rejected, application.getStatus());
        verify(history).save(argThat(entry -> entry.getChangedByRole() == ActorRole.ROLE_SYSTEM));
        verify(notifications, never()).notifyOfferDeadlineExtended(any());
    }

    @Test
    void invalidDeadlineExtensionDoesNotSendNotification() {
        application.setStatus(ApplicationStatus.Success);
        LocalDateTime deadline = LocalDateTime.now(ZoneId.of("Asia/Hong_Kong")).plusHours(1);
        application.setAcceptanceDeadline(deadline);

        assertThrows(ResponseStatusException.class, () -> service.extendDeadline(42L, "APP-1", deadline, "System fault"));
        assertEquals(deadline, application.getAcceptanceDeadline());
        verify(notifications, never()).notifyOfferDeadlineExtended(any());
    }
}
