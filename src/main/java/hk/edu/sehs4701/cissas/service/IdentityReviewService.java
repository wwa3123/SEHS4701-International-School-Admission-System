package hk.edu.sehs4701.cissas.service;

import hk.edu.sehs4701.cissas.entity.Admin;
import hk.edu.sehs4701.cissas.entity.Application;
import hk.edu.sehs4701.cissas.entity.ApplicantSnapshot;
import hk.edu.sehs4701.cissas.entity.IdentityReview;
import hk.edu.sehs4701.cissas.enums.IdentityDocumentType;
import hk.edu.sehs4701.cissas.repository.AdminRepository;
import hk.edu.sehs4701.cissas.repository.ApplicationRepository;
import hk.edu.sehs4701.cissas.repository.IdentityReviewLockRepository;
import hk.edu.sehs4701.cissas.repository.IdentityReviewRepository;
import org.springframework.http.HttpStatus;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
public class IdentityReviewService {
    private final IdentityReviewRepository reviews;
    private final IdentityReviewLockRepository lock;
    private final ApplicationRepository applications;
    private final AdminRepository admins;

    public IdentityReviewService(IdentityReviewRepository reviews, IdentityReviewLockRepository lock,
                                 ApplicationRepository applications, AdminRepository admins) {
        this.reviews = reviews;
        this.lock = lock;
        this.applications = applications;
        this.admins = admins;
    }

    public record ApplicationSummary(String applicationNumber, String fullName, LocalDate dateOfBirth,
                                     IdentityDocumentType documentType, String status) {}

    public record Candidate(ApplicationSummary flagged, ApplicationSummary possibleMatch) {}

    public record CandidatePage(List<Candidate> items, String nextCursor) {}

    public record ApplicationEvidence(String applicationNumber, String fullName, LocalDate dateOfBirth,
                                      String gender, String phone, String previousSchool,
                                      IdentityDocumentType documentType, String documentNumber,
                                      String issuingCountry, String academicYear, String status) {}

    public record Decision(String flaggedApplicationNumber, String candidateApplicationNumber, boolean samePerson,
                           String reviewedBy, LocalDateTime reviewedAt, boolean current) {}

    public record HistoryPage(List<Decision> items, String nextCursor) {}

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
        for (Object[] pair : pairs) {
            Application flagged = (Application) pair[0];
            Application candidate = (Application) pair[1];
            Long firstId = flagged.getApplicant().getId();
            Long secondId = candidate.getApplicant().getId();
            if (matches(flagged, candidate)
                    && !hasResolvedMatch(groups.computeIfAbsent(firstId, id -> connected(graph, id)),
                            groups.computeIfAbsent(secondId, id -> connected(graph, id)))) {
                result.add(new Candidate(summary(flagged), summary(candidate)));
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
        return new ApplicationEvidence(application.getApplicationNumber(), snapshot.getFullName(), snapshot.getDateOfBirth(),
                snapshot.getGender(), snapshot.getPhone(), snapshot.getPreviousSchool(), snapshot.getDocumentType(),
                snapshot.getDocumentNumber(), snapshot.getIssuingCountry(), application.getAcademicYear().getName(),
                application.getStatus().name());
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, isolation = Isolation.READ_COMMITTED)
    public void review(Long adminId, String flaggedNumber, String candidateNumber, boolean samePerson) {
        lock.acquire();

        Admin admin = activeAdmin(adminId);
        Application flagged = application(flaggedNumber);
        Application candidate = application(candidateNumber);
        Long firstId = flagged.getApplicant().getId();
        Long secondId = candidate.getApplicant().getId();

        if (firstId.equals(secondId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Applications belong to the same applicant");
        }

        IdentityReview previous = reviews.currentPair(firstId, secondId).orElse(null);
        if (previous != null && previous.isSamePerson() == samePerson) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This decision is already recorded");
        }

        Set<Long> firstGroup = confirmedApplicantIds(firstId, previous);
        Set<Long> secondGroup = confirmedApplicantIds(secondId, previous);
        if (firstGroup.contains(secondId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Applicants are already confirmed as the same person");
        }

        if (previous != null) {
            previous.supersede();
            reviews.saveAndFlush(previous);
        }

        if (samePerson && reviews.hasDifferentDecision(firstGroup, secondGroup)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Confirmation contradicts a previous review");
        }

        reviews.save(new IdentityReview(flagged, candidate, admin, samePerson));
    }

    Set<Long> confirmedApplicantIds(Long applicantId) {
        return confirmedApplicantIds(applicantId, null);
    }

    boolean hasResolvedMatch(Set<Long> firstGroup, Long secondApplicantId) {
        return firstGroup.contains(secondApplicantId)
                || hasResolvedMatch(firstGroup, confirmedApplicantIds(secondApplicantId));
    }

    private boolean hasResolvedMatch(Set<Long> firstGroup, Set<Long> secondGroup) {
        return !java.util.Collections.disjoint(firstGroup, secondGroup)
                || reviews.hasDifferentDecision(firstGroup, secondGroup);
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
        return first.getProgrammeLevel().getId().equals(second.getProgrammeLevel().getId())
                && first.getAcademicYear().getId().equals(second.getAcademicYear().getId())
                && a.getDateOfBirth().equals(b.getDateOfBirth())
                && opposite(a.getDocumentType()) == b.getDocumentType()
                && ApplicantSnapshot.normalizeName(a.getFullName())
                        .equals(ApplicantSnapshot.normalizeName(b.getFullName()));
    }

    private IdentityDocumentType opposite(IdentityDocumentType type) {
        if (type == IdentityDocumentType.HKID) return IdentityDocumentType.PASSPORT;
        if (type == IdentityDocumentType.PASSPORT) return IdentityDocumentType.HKID;
        return null;
    }

    private ApplicationSummary summary(Application application) {
        var snapshot = application.getApplicantSnapshot();
        return new ApplicationSummary(application.getApplicationNumber(), snapshot.getFullName(),
                snapshot.getDateOfBirth(), snapshot.getDocumentType(), application.getStatus().name());
    }

    private Application application(String number) {
        return applications.findByApplicationNumber(number)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Application not found"));
    }

    private Admin activeAdmin(Long id) {
        return admins.findById(id).filter(Admin::isActive)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "Active admin required"));
    }
}
