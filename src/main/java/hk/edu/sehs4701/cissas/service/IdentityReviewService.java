package hk.edu.sehs4701.cissas.service;

import hk.edu.sehs4701.cissas.dto.response.ApplicationEvidence;
import hk.edu.sehs4701.cissas.dto.response.ApplicationSummary;
import hk.edu.sehs4701.cissas.dto.response.Candidate;
import hk.edu.sehs4701.cissas.dto.response.CandidatePage;
import hk.edu.sehs4701.cissas.dto.response.Decision;
import hk.edu.sehs4701.cissas.dto.response.HistoryPage;
import hk.edu.sehs4701.cissas.entity.Admin;
import hk.edu.sehs4701.cissas.entity.Application;
import hk.edu.sehs4701.cissas.entity.ApplicantSnapshot;
import hk.edu.sehs4701.cissas.entity.IdentityReview;
import hk.edu.sehs4701.cissas.entity.StatusHistory;
import hk.edu.sehs4701.cissas.enums.ActorRole;
import hk.edu.sehs4701.cissas.enums.ApplicationStatus;
import hk.edu.sehs4701.cissas.repository.AdminRepository;
import hk.edu.sehs4701.cissas.repository.ApplicationRepository;
import hk.edu.sehs4701.cissas.repository.IdentityReviewLockRepository;
import hk.edu.sehs4701.cissas.repository.IdentityReviewRepository;
import hk.edu.sehs4701.cissas.repository.ProgrammeLevelRepository;
import hk.edu.sehs4701.cissas.repository.StatusHistoryRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.time.LocalDateTime;
import java.time.ZoneId;

@Service
public class IdentityReviewService {
    private final IdentityReviewRepository reviews;
    private final IdentityReviewLockRepository lock;
    private final ApplicationRepository applications;
    private final AdminRepository admins;
    private final ProgrammeLevelRepository programmeLevels;
    private final StatusHistoryRepository statusHistory;
    private final NotificationService notifications;

    @Autowired
    public IdentityReviewService(IdentityReviewRepository reviews, IdentityReviewLockRepository lock,
                                 ApplicationRepository applications, AdminRepository admins,
                                 ProgrammeLevelRepository programmeLevels, StatusHistoryRepository statusHistory,
                                 NotificationService notifications) {
        this.reviews = reviews;
        this.lock = lock;
        this.applications = applications;
        this.admins = admins;
        this.programmeLevels = programmeLevels;
        this.statusHistory = statusHistory;
        this.notifications = notifications;
    }

    @Transactional(readOnly = true)
    public HistoryPage history(Long adminId, String cursor, int size) {
        activeAdmin(adminId);

        if (size < 1 || size > 100) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Size must be between 1 and 100");
        }

        Long beforeId = null;
        if (cursor != null) {
            try {
                if (!cursor.matches("[1-9][0-9]*")) throw new NumberFormatException();
                beforeId = Long.parseLong(cursor);
            } catch (NumberFormatException exception) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid history cursor");
            }
        }

        var page = reviews.findHistory(beforeId, PageRequest.of(0, size));
        var items = page.stream()
                .map(review -> new Decision(review.getFirstApplication().getApplicationNumber(),
                        review.getSecondApplication().getApplicationNumber(), review.isSamePerson(),
                        review.getReviewedBy().getId().toString(), review.getReviewedAt(), review.getCurrentMarker() != null))
                .toList();
        String nextCursor = page.hasNext() ? page.getContent().getLast().getId().toString() : null;

        return new HistoryPage(items, nextCursor);
    }

    @Transactional(readOnly = true)
    public CandidatePage pending(Long adminId, String cursor, int size) {
        activeAdmin(adminId);

        if (size < 1 || size > 100) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Size must be between 1 and 100");
        }

        Long afterFlagged = null;
        Long afterCandidate = null;
        if (cursor != null) {
            String[] parts = cursor.split(":", -1);
            try {
                if (parts.length != 2 || !parts[0].matches("-?\\d+") || !parts[1].matches("-?\\d+")) {
                    throw new NumberFormatException();
                }
                afterFlagged = Long.parseLong(parts[0]);
                afterCandidate = Long.parseLong(parts[1]);
            } catch (NumberFormatException exception) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid review cursor");
            }
        }

        var pairs = applications.findPotentialReviewPairs(afterFlagged, afterCandidate, PageRequest.of(0, size));
        Set<Long> roots = new HashSet<>();
        for (Object[] pair : pairs) {
            roots.add(((Application) pair[0]).getApplicant().getId());
            roots.add(((Application) pair[1]).getApplicant().getId());
        }

        var graph = confirmedGraph(roots, null);
        var groups = new HashMap<Long, Set<Long>>();
        List<Candidate> result = new ArrayList<>();
        LocalDateTime now = LocalDateTime.now(ZoneId.of("Asia/Hong_Kong"));
        for (Object[] pair : pairs) {
            Application flagged = (Application) pair[0];
            Application candidate = (Application) pair[1];
            Long firstId = flagged.getApplicant().getId();
            Long secondId = candidate.getApplicant().getId();
            if (matches(flagged, candidate)
                    && !reviews.hasDifferentDecision(
                            groups.computeIfAbsent(firstId, id -> connected(graph, id)),
                            groups.computeIfAbsent(secondId, id -> connected(graph, id)))) {
                result.add(new Candidate(summary(flagged, now), summary(candidate, now)));
            }
        }

        String nextCursor = null;
        if (pairs.hasNext()) {
            Object[] last = pairs.getContent().getLast();
            nextCursor = ((Application) last[0]).getId() + ":" + ((Application) last[1]).getId();
        }

        return new CandidatePage(result, nextCursor);
    }

    @Transactional(readOnly = true)
    public ApplicationEvidence evidence(Long adminId, String applicationNumber) {
        activeAdmin(adminId);
        Application application = application(applicationNumber);
        var snapshot = application.getApplicantSnapshot();
        return new ApplicationEvidence(application.getApplicationNumber(), application.getMember().getEmail(),
                snapshot.getFullName(), snapshot.getDateOfBirth(),
                snapshot.getGender(), snapshot.getPhone(), snapshot.getPreviousSchool(), snapshot.getDocumentType(),
                snapshot.getDocumentNumber(), snapshot.getIssuingCountry(), application.getAcademicYear().getName(),
                application.effectiveStatus(LocalDateTime.now(ZoneId.of("Asia/Hong_Kong"))).name());
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void review(Long adminId, String flaggedNumber, String candidateNumber, boolean samePerson) {
        lock.acquire();
        Admin admin = activeAdmin(adminId);
        Application initial = application(flaggedNumber);
        Application candidate = application(candidateNumber);
        programmeLevels.findLockedById(initial.getProgrammeLevel().getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Programme level not found"));
        Application flagged = applications.findLockedByApplicationNumber(flaggedNumber)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Application not found"));
        Long firstId = flagged.getApplicant().getId();
        Long secondId = candidate.getApplicant().getId();

        if (candidate.getStatus() == ApplicationStatus.Cancelled) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "The matching application is cancelled");
        }
        if (firstId.equals(secondId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Applications belong to the same applicant");
        }
        if (!matches(flagged, candidate)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "The applications do not match on name and date of birth");
        }
        if (flagged.getStatus() != ApplicationStatus.Pending || !flagged.isManualIdentityReviewRequired()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "The duplicate application is no longer pending review");
        }

        IdentityReview previous = reviews.currentPair(firstId, secondId).orElse(null);
        if (previous != null && previous.isSamePerson() == samePerson && !samePerson) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This decision is already recorded");
        }

        Set<Long> firstGroup = confirmedApplicantIds(firstId, previous);
        Set<Long> secondGroup = confirmedApplicantIds(secondId, previous);
        if (firstGroup.contains(secondId) && !samePerson) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Applicants are already confirmed as the same person");
        }

        if (previous != null && previous.isSamePerson() != samePerson) {
            previous.supersede();
            reviews.saveAndFlush(previous);
        }

        if (samePerson && reviews.hasDifferentDecision(firstGroup, secondGroup)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Confirmation contradicts a previous review");
        }

        if (previous == null || previous.isSamePerson() != samePerson) {
            reviews.saveAndFlush(new IdentityReview(flagged, candidate, admin, samePerson));
        }
        if (samePerson) {
            flagged.setStatus(ApplicationStatus.Cancelled);
            flagged.setManualIdentityReviewRequired(false);
            applications.save(flagged);
            StatusHistory cancellation = new StatusHistory();
            cancellation.setApplication(flagged);
            cancellation.setFromStatus(ApplicationStatus.Pending);
            cancellation.setToStatus(ApplicationStatus.Cancelled);
            cancellation.setChangedByRole(admin.getRole());
            cancellation.setChangedById(admin.getId());
            cancellation.setRemarks("Duplicate application; administrator confirmed same applicant");
            cancellation.setChangedAt(LocalDateTime.now(ZoneId.of("Asia/Hong_Kong")));
            statusHistory.save(cancellation);
            notifications.notifyApplicationCancelled(flagged);
        } else {
            flagged.setManualIdentityReviewRequired(hasPossibleDuplicate(flagged));
            applications.save(flagged);
        }
    }

    boolean hasPossibleDuplicate(Application application) {
        var snapshot = application.getApplicantSnapshot();
        return applications.findAllByStatusNotAndApplicantSnapshot_DateOfBirthAndApplicantSnapshot_NameHash(
                        ApplicationStatus.Cancelled, snapshot.getDateOfBirth(), snapshot.getNameHash()).stream()
                .anyMatch(candidate -> matches(application, candidate)
                        && lacksDifferentDecision(application.getApplicant().getId(), candidate.getApplicant().getId()));
    }

    Set<Long> confirmedApplicantIds(Long applicantId) {
        return confirmedApplicantIds(applicantId, null);
    }

    boolean lacksDifferentDecision(Long firstApplicantId, Long secondApplicantId) {
        return secondApplicantId == null || !reviews.hasDifferentDecision(
                confirmedApplicantIds(firstApplicantId), confirmedApplicantIds(secondApplicantId));
    }

    private Set<Long> confirmedApplicantIds(Long id, IdentityReview excluded) {
        return connected(confirmedGraph(Set.of(id), excluded), id);
    }

    private HashMap<Long, List<Long>> confirmedGraph(Set<Long> roots, IdentityReview excluded) {
        Set<Long> ids = new HashSet<>(roots);
        Set<Long> frontier = new HashSet<>(roots);
        var neighbours = new HashMap<Long, List<Long>>();
        while (!frontier.isEmpty()) {
            Set<Long> next = new HashSet<>();
            for (IdentityReview review : reviews.confirmedTouching(frontier)) {
                if (review == excluded || (excluded != null && excluded.getId() != null
                        && excluded.getId().equals(review.getId()))) continue;
                Long first = review.getFirstApplicant().getId();
                Long second = review.getSecondApplicant().getId();
                neighbours.computeIfAbsent(first, ignored -> new ArrayList<>()).add(second);
                neighbours.computeIfAbsent(second, ignored -> new ArrayList<>()).add(first);
                next.add(first);
                next.add(second);
            }
            next.removeAll(ids);
            ids.addAll(next);
            frontier = next;
        }
        return neighbours;
    }

    private Set<Long> connected(HashMap<Long, List<Long>> neighbours, Long id) {
        Set<Long> ids = new HashSet<>();
        ids.add(id);
        var queue = new ArrayDeque<Long>();
        queue.add(id);
        while (!queue.isEmpty()) {
            for (Long neighbour : neighbours.getOrDefault(queue.remove(), List.of())) {
                if (ids.add(neighbour)) {
                    queue.add(neighbour);
                }
            }
        }
        return ids;
    }

    private boolean matches(Application first, Application second) {
        var a = first.getApplicantSnapshot();
        var b = second.getApplicantSnapshot();
        return !first.getApplicant().getId().equals(second.getApplicant().getId())
                && a.getDateOfBirth().equals(b.getDateOfBirth())
                && ApplicantSnapshot.normalizeName(a.getFullName())
                        .equals(ApplicantSnapshot.normalizeName(b.getFullName()));
    }

    private ApplicationSummary summary(Application application, LocalDateTime now) {
        var snapshot = application.getApplicantSnapshot();
        return new ApplicationSummary(application.getApplicationNumber(), snapshot.getFullName(),
                snapshot.getDateOfBirth(), snapshot.getDocumentType(), application.effectiveStatus(now).name());
    }

    private Application application(String number) {
        return applications.findByApplicationNumber(number)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Application not found"));
    }

    private Admin activeAdmin(Long id) {
        return admins.findById(id).filter(Admin::isActive)
                .filter(admin -> admin.getRole() == ActorRole.ROLE_ADMIN
                        || admin.getRole() == ActorRole.ROLE_SUPER_ADMIN)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "Active admin required"));
    }
}
