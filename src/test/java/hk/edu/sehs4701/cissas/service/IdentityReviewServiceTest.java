package hk.edu.sehs4701.cissas.service;

import hk.edu.sehs4701.cissas.entity.*;
import hk.edu.sehs4701.cissas.enums.ApplicationStatus;
import hk.edu.sehs4701.cissas.enums.IdentityDocumentType;
import hk.edu.sehs4701.cissas.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.SliceImpl;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class IdentityReviewServiceTest {
    private final IdentityReviewRepository reviews = mock(IdentityReviewRepository.class);
    private final IdentityReviewLockRepository lock = mock(IdentityReviewLockRepository.class);
    private final ApplicationRepository applications = mock(ApplicationRepository.class);
    private final AdminRepository admins = mock(AdminRepository.class);
    private final IdentityReviewService service = new IdentityReviewService(reviews, lock, applications, admins);
    private final Admin admin = new Admin();
    private Application flagged;
    private Application candidate;

    @BeforeEach
    void setUp() {
        flagged = application(1L, "APP-1", IdentityDocumentType.HKID);
        flagged.setManualIdentityReviewRequired(true);
        candidate = application(2L, "APP-2", IdentityDocumentType.PASSPORT);
        when(admins.findById(9L)).thenReturn(Optional.of(admin));
        when(applications.findByApplicationNumber("APP-1")).thenReturn(Optional.of(flagged));
        when(applications.findByApplicationNumber("APP-2")).thenReturn(Optional.of(candidate));
    }

    @Test
    void recordsBothDecisionsWithoutMergingApplicants() {
        service.review(9L, "APP-1", "APP-2", false);

        ArgumentCaptor<IdentityReview> saved = ArgumentCaptor.forClass(IdentityReview.class);
        verify(reviews).save(saved.capture());
        assertFalse(saved.getValue().isSamePerson());
        assertSame(flagged.getApplicant(), saved.getValue().getFirstApplicant());
        assertSame(candidate.getApplicant(), saved.getValue().getSecondApplicant());
        assertSame(admin, saved.getValue().getReviewedBy());
        verify(lock).acquire();
    }

    @Test
    void confirmationConnectsApplicantsTransitively() {
        Application third = application(3L, "APP-3", IdentityDocumentType.HKID);
        existing(List.of(
                new IdentityReview(flagged, candidate, admin, true),
                new IdentityReview(candidate, third, admin, true)));

        assertEquals(Set.of(1L, 2L, 3L), service.confirmedApplicantIds(3L));
    }

    @Test
    void rejectsConfirmationContradictingEarlierDifferentPeopleDecision() {
        Application third = application(3L, "APP-3", IdentityDocumentType.HKID);
        existing(List.of(
                new IdentityReview(flagged, third, admin, true),
                new IdentityReview(candidate, third, admin, false)));

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> service.review(9L, "APP-1", "APP-2", true));

        assertEquals(HttpStatus.CONFLICT, error.getStatusCode());
        verify(reviews, never()).save(any());
    }

    @Test
    void rejectsDifferentPeopleDecisionWhenAlreadyTransitivelyConfirmed() {
        Application third = application(3L, "APP-3", IdentityDocumentType.HKID);
        existing(List.of(
                new IdentityReview(flagged, third, admin, true),
                new IdentityReview(candidate, third, admin, true)));
        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> service.review(9L, "APP-1", "APP-2", false));
        assertEquals(HttpStatus.CONFLICT, error.getStatusCode());
    }

    @Test
    void rejectsRedundantConfirmationToKeepFutureCorrectionsPossible() {
        Application third = application(3L, "APP-3", IdentityDocumentType.HKID);
        existing(List.of(
                new IdentityReview(flagged, third, admin, true),
                new IdentityReview(candidate, third, admin, true)));
        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> service.review(9L, "APP-1", "APP-2", true));
        assertEquals(HttpStatus.CONFLICT, error.getStatusCode());
        verify(reviews, never()).save(any());
    }

    @Test
    void pendingShowsUnreviewedCandidatesAndOmitsReviewedPairs() {
        when(applications.findPotentialReviewPairs(isNull(), isNull(), any())).thenReturn(new SliceImpl<>(java.util.Collections.singletonList(new Object[]{flagged, candidate})));
        assertEquals(1, service.pending(9L, null, 25).items().size());
        when(applications.findPotentialReviewPairs(isNull(), isNull(), any())).thenReturn(new SliceImpl<>(List.of()));
        assertTrue(service.pending(9L, null, 25).items().isEmpty());
    }

    @Test
    void cursorAdvancesAcrossAFilteredEmptyPage() {
        Application third = application(3L, "APP-3", IdentityDocumentType.HKID);
        existing(List.of(new IdentityReview(flagged, third, admin, true),
                new IdentityReview(third, candidate, admin, true)));
        when(applications.findPotentialReviewPairs(isNull(), isNull(), any()))
                .thenReturn(new SliceImpl<>(java.util.Collections.singletonList(new Object[]{flagged, candidate}),
                        PageRequest.of(0, 1), true));
        when(applications.findPotentialReviewPairs(eq(1L), eq(2L), any())).thenReturn(new SliceImpl<>(List.of()));
        var first = service.pending(9L, null, 1);
        assertTrue(first.items().isEmpty());
        assertEquals("1:2", first.nextCursor());
        assertNull(service.pending(9L, first.nextCursor(), 1).nextCursor());
    }

    @Test
    void groupDecisionSuppressesPairWhenBothSidesHaveConfirmedLinks() {
        Application third = application(3L, "APP-3", IdentityDocumentType.PASSPORT);
        Application fourth = application(4L, "APP-4", IdentityDocumentType.HKID);
        existing(List.of(new IdentityReview(flagged, fourth, admin, true),
                new IdentityReview(candidate, third, admin, true),
                new IdentityReview(fourth, third, admin, false)));
        when(applications.findPotentialReviewPairs(isNull(), isNull(), any()))
                .thenReturn(new SliceImpl<>(java.util.Collections.singletonList(new Object[]{flagged, candidate})));
        assertTrue(service.hasResolvedMatch(Set.of(1L, 4L), 2L));
        assertTrue(service.pending(9L, null, 25).items().isEmpty());
    }

    @Test
    void supersededDifferentDecisionDoesNotSuppressAnUnresolvedPair() {
        Application third = application(3L, "APP-3", IdentityDocumentType.HKID);
        IdentityReview different = new IdentityReview(third, candidate, admin, false);
        different.supersede();
        existing(List.of(new IdentityReview(flagged, third, admin, true), different));
        when(applications.findPotentialReviewPairs(isNull(), isNull(), any()))
                .thenReturn(new SliceImpl<>(java.util.Collections.singletonList(new Object[]{flagged, candidate})));
        assertFalse(service.hasResolvedMatch(Set.of(1L, 3L), 2L));
        assertEquals(1, service.pending(9L, null, 25).items().size());
    }

    @Test
    void correctionRetainsOriginalDecisionAndRecordsNewOne() {
        IdentityReview previous = new IdentityReview(flagged, candidate, admin, false);
        existing(List.of(previous));
        service.review(9L, "APP-1", "APP-2", true);
        assertNull(previous.getCurrentMarker());
        verify(reviews).saveAndFlush(previous);
        verify(reviews).save(argThat(review -> review.isSamePerson() && review.getCurrentMarker() == 1));
    }

    @Test
    void historyIncludesSupersededDecisions() {
        admin.setId(9007199254740993L);
        IdentityReview previous = new IdentityReview(flagged, candidate, admin, false);
        previous.supersede();
        when(reviews.findHistory(isNull(), any())).thenReturn(new SliceImpl<>(List.of(previous)));
        var result = service.history(9L, null, 25);
        assertEquals(1, result.items().size());
        assertEquals("APP-1", result.items().getFirst().flaggedApplicationNumber());
        assertEquals("9007199254740993", result.items().getFirst().reviewedBy());
        assertFalse(result.items().getFirst().current());
        assertNull(result.nextCursor());
    }

    @Test
    void historyRejectsInvalidCursorAndPageSize() {
        assertThrows(ResponseStatusException.class, () -> service.history(9L, "-1", 25));
        assertThrows(ResponseStatusException.class, () -> service.history(9L, "9223372036854775808", 25));
        assertThrows(ResponseStatusException.class, () -> service.history(9L, null, 0));
        assertThrows(ResponseStatusException.class, () -> service.history(9L, null, 101));
    }

    @Test
    void refusesInactiveAdminsAndAllowsUnmatchedApplications() {
        admin.setActive(false);
        assertEquals(HttpStatus.FORBIDDEN, assertThrows(ResponseStatusException.class,
                () -> service.review(9L, "APP-1", "APP-2", true)).getStatusCode());
        admin.setActive(true);
        candidate.getProgrammeLevel().setId(8L);
        service.review(9L, "APP-1", "APP-2", true);
        verify(reviews).save(any(IdentityReview.class));
    }

    @Test
    void evidenceIncludesSubmittedDocumentAndRequiresActiveAdmin() {
        flagged.getAcademicYear().setName("2026/27");
        var evidence = service.evidence(9L, "APP-1");
        assertEquals("123456", evidence.documentNumber());
        assertEquals("2026/27", evidence.academicYear());
        admin.setActive(false);
        assertThrows(ResponseStatusException.class, () -> service.evidence(9L, "APP-1"));
    }

    @Test
    void rejectsInvalidPageAndSelfReview() {
        assertThrows(ResponseStatusException.class, () -> service.pending(9L, "1:bad", 25));
        assertThrows(ResponseStatusException.class, () -> service.pending(9L, "9223372036854775808:1", 25));
        assertThrows(ResponseStatusException.class, () -> service.pending(9L, null, 101));
        candidate.setApplicant(flagged.getApplicant());
        assertEquals(HttpStatus.BAD_REQUEST, assertThrows(ResponseStatusException.class,
                () -> service.review(9L, "APP-1", "APP-2", true)).getStatusCode());
    }

    private Application application(long applicantId, String number, IdentityDocumentType type) {
        Applicant applicant = new Applicant();
        applicant.setId(applicantId);
        ProgrammeLevel level = new ProgrammeLevel();
        level.setId(7L);
        AcademicYear year = new AcademicYear();
        year.setId(8L);
        Application application = new Application();
        application.setId(applicantId);
        application.setApplicant(applicant);
        application.setApplicationNumber(number);
        application.setProgrammeLevel(level);
        application.setAcademicYear(year);
        application.setStatus(ApplicationStatus.Pending);
        application.setApplicantSnapshot(new ApplicantSnapshot("Example Name", LocalDate.of(2018, 1, 1),
                "Unspecified", "12345678", "School", type, "123456", null));
        return application;
    }

    private void existing(List<IdentityReview> existing) {
        when(reviews.currentPair(anyLong(), anyLong())).thenAnswer(inv -> existing.stream()
                .filter(r -> (r.getFirstApplicant().getId().equals(inv.getArgument(0))
                        && r.getSecondApplicant().getId().equals(inv.getArgument(1)))
                        || (r.getFirstApplicant().getId().equals(inv.getArgument(1))
                        && r.getSecondApplicant().getId().equals(inv.getArgument(0))))
                .findFirst());
        when(reviews.confirmedTouching(anyCollection())).thenAnswer(inv -> {
            java.util.Collection<Long> ids = inv.getArgument(0);
            return existing.stream().filter(IdentityReview::isSamePerson)
                    .filter(r -> ids.contains(r.getFirstApplicant().getId()) || ids.contains(r.getSecondApplicant().getId()))
                    .toList();
        });
        when(reviews.hasDifferentDecision(anyCollection(), anyCollection())).thenAnswer(inv -> {
            java.util.Collection<Long> first = inv.getArgument(0);
            java.util.Collection<Long> second = inv.getArgument(1);
            return existing.stream().filter(r -> !r.isSamePerson() && r.getCurrentMarker() != null)
                    .anyMatch(r -> (first.contains(r.getFirstApplicant().getId()) && second.contains(r.getSecondApplicant().getId()))
                            || (second.contains(r.getFirstApplicant().getId()) && first.contains(r.getSecondApplicant().getId())));
        });
    }
}
