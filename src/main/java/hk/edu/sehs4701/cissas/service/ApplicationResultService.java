package hk.edu.sehs4701.cissas.service;

import hk.edu.sehs4701.cissas.dto.request.OfferDecisionRequest;
import hk.edu.sehs4701.cissas.dto.response.ApplicationSummary;
import hk.edu.sehs4701.cissas.dto.response.OfferConfirmation;
import hk.edu.sehs4701.cissas.dto.response.MemberApplicationResult;
import hk.edu.sehs4701.cissas.dto.response.AdminApplication;
import hk.edu.sehs4701.cissas.dto.response.MemberApplication;
import hk.edu.sehs4701.cissas.entity.Admin;
import hk.edu.sehs4701.cissas.entity.Application;
import hk.edu.sehs4701.cissas.entity.StatusHistory;
import hk.edu.sehs4701.cissas.enums.ActorRole;
import hk.edu.sehs4701.cissas.enums.ApplicationStatus;
import hk.edu.sehs4701.cissas.repository.AdminRepository;
import hk.edu.sehs4701.cissas.repository.ApplicationRepository;
import hk.edu.sehs4701.cissas.repository.ProgrammeLevelRepository;
import hk.edu.sehs4701.cissas.repository.StatusHistoryRepository;
import hk.edu.sehs4701.cissas.repository.IdentityReviewLockRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Slice;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

@Service
public class ApplicationResultService {
    private static final ZoneId HONG_KONG = ZoneId.of("Asia/Hong_Kong");
    private static final List<ApplicationStatus> SEAT_RESERVING_STATUSES =
            List.of(ApplicationStatus.Success, ApplicationStatus.Accepted);

    private final ApplicationRepository applications;
    private final ProgrammeLevelRepository programmeLevels;
    private final StatusHistoryRepository history;
    private final AdminRepository admins;
    private final NotificationService notifications;
    private final IdentityReviewService duplicates;
    private final IdentityReviewLockRepository duplicateLock;

    @Autowired
    public ApplicationResultService(ApplicationRepository applications, ProgrammeLevelRepository programmeLevels,
                                    StatusHistoryRepository history, AdminRepository admins,
                                    NotificationService notifications, IdentityReviewService duplicates,
                                    IdentityReviewLockRepository duplicateLock) {
        this.applications = applications;
        this.programmeLevels = programmeLevels;
        this.history = history;
        this.admins = admins;
        this.notifications = notifications;
        this.duplicates = duplicates;
        this.duplicateLock = duplicateLock;
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public boolean setResult(Long adminId, String applicationNumber, ApplicationStatus result, String reason) {
        Admin admin = activeAdmin(adminId);
        validateResult(result);
        duplicateLock.acquire();
        Long levelId = applications.findProgrammeLevelId(applicationNumber)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Application not found"));
        var level = programmeLevels.findLockedById(levelId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Programme level not found"));
        Application application = applications.findLockedByApplicationNumber(applicationNumber)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Application not found"));
        ApplicationStatus previous = application.getStatus();
        LocalDateTime now = LocalDateTime.now(HONG_KONG);
        if (previous == ApplicationStatus.Success && application.getRespondedAt() == null
                && application.getAcceptanceDeadline() != null
                && !now.isBefore(application.getAcceptanceDeadline())) {
            expire(application, now);
            return false;
        }
        if (application.isManualIdentityReviewRequired()) {
            if (duplicates.hasPossibleDuplicate(application)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Resolve possible duplicate records before setting a result");
            }
            application.setManualIdentityReviewRequired(false);
        }
        boolean correction = previous != ApplicationStatus.Pending
                && !(previous == ApplicationStatus.Wait && result == ApplicationStatus.Success);

        if (previous.isFinal() || previous == result) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This result transition is not allowed");
        }
        if (correction) {
            if (admin.getRole() != ActorRole.ROLE_SUPER_ADMIN) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only a super administrator may correct a result");
            }
            if (application.getRespondedAt() != null) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "A responded offer cannot be corrected");
            }
            if (reason == null || reason.isBlank()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A correction reason is required");
            }
        }
        if (!LocalDate.now(HONG_KONG).isAfter(level.getApplicationEndDate())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Results may only be set after the closing date");
        }

        if (result == ApplicationStatus.Success) {
            expireOffersForLevel(level.getId(), now);
            long reservedSeats = applications.countByProgrammeLevel_IdAndStatusIn(
                    level.getId(), SEAT_RESERVING_STATUSES);
            if (reservedSeats >= level.getSeatQuota()) {
                return false;
            }
        }

        application.setStatus(result);
        application.setResultReleasedAt(now);
        application.setAcceptanceDeadline(result == ApplicationStatus.Success ? now.plusHours(168) : null);
        application.setRespondedAt(null);
        applications.save(application);
        recordHistory(application, previous, result, admin.getRole(), admin.getId(), correction ? reason.trim() : null, now);
        notifications.notifyApplicationResult(application, correction, correction ? reason.trim() : null,
                previous == ApplicationStatus.Wait && result == ApplicationStatus.Success);
        return true;
    }

    @Transactional(readOnly = true)
    public List<ApplicationSummary> waitingList(Long adminId, Long programmeLevelId) {
        activeAdmin(adminId);
        if (!programmeLevels.existsById(programmeLevelId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Programme level not found");
        }
        return applications.findAllByProgrammeLevel_IdAndStatus(programmeLevelId, ApplicationStatus.Wait).stream()
                .map(application -> new ApplicationSummary(application.getApplicationNumber(),
                        application.getApplicantSnapshot().getFullName(),
                        application.getApplicantSnapshot().getDateOfBirth(),
                        application.getApplicantSnapshot().getDocumentType(), application.getStatus().name()))
                .toList();
    }

    @Transactional(readOnly = true)
    public Slice<AdminApplication> listApplications(Long adminId, Long programmeLevelId,
                                                     ApplicationStatus status, int page, int size) {
        activeAdmin(adminId);
        PageRequest pageable = pageRequest(page, size);
        if (programmeLevelId != null && !programmeLevels.existsById(programmeLevelId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Programme level not found");
        }
        LocalDateTime now = LocalDateTime.now(HONG_KONG);
        return applications.findForAdministration(programmeLevelId, status, now, pageable)
                .map(application -> new AdminApplication(application.getApplicationNumber(),
                        application.getProgrammeLevel().getId(), application.getProgrammeLevel().getProgrammeName(),
                        application.getApplicantSnapshot().getFullName(), application.effectiveStatus(now)));
    }

    @Transactional(readOnly = true)
    public Slice<MemberApplication> memberApplications(Long memberId, int page, int size) {
        return applications.findAllByMember_IdOrderByIdAsc(memberId, pageRequest(page, size))
                .map(application -> new MemberApplication(application.getApplicationNumber(),
                        application.getProgrammeLevel().getProgrammeName()));
    }

    private PageRequest pageRequest(int page, int size) {
        if (page < 0 || size < 1 || size > 100) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Page must be nonnegative and size between 1 and 100");
        }
        return PageRequest.of(page, size);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public MemberApplicationResult memberResult(Long memberId, String applicationNumber) {
        Long levelId = applications.findProgrammeLevelIdForMember(applicationNumber, memberId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Application not found"));
        programmeLevels.findLockedById(levelId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Programme level not found"));
        Application application = applications.findLockedByApplicationNumberAndMember_Id(applicationNumber, memberId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Application not found"));
        LocalDateTime now = LocalDateTime.now(HONG_KONG);
        if (application.getStatus() == ApplicationStatus.Success && application.getAcceptanceDeadline() != null
                && !now.isBefore(application.getAcceptanceDeadline())) {
            expire(application, now);
        }
        return new MemberApplicationResult(application.getApplicationNumber(),
                application.getProgrammeLevel().getProgrammeName(), application.getStatus(),
                application.getResultReleasedAt(), application.getAcceptanceDeadline());
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public OfferConfirmation offer(Long memberId, String applicationNumber, OfferDecisionRequest.Decision decision) {
        if (decision == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A decision is required");
        }
        Long levelId = applications.findProgrammeLevelIdForMember(applicationNumber, memberId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Application not found"));
        programmeLevels.findLockedById(levelId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Programme level not found"));
        Application application = applications.findLockedByApplicationNumberAndMember_Id(applicationNumber, memberId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Application not found"));
        if (application.getStatus() != ApplicationStatus.Success || application.getAcceptanceDeadline() == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "No offer is awaiting a response");
        }
        LocalDateTime now = LocalDateTime.now(HONG_KONG);
        if (!now.isBefore(application.getAcceptanceDeadline())) {
            expire(application, now);
            return null;
        }
        return new OfferConfirmation(application.getProgrammeLevel().getProgrammeName(),
                application.getApplicationNumber(), application.getAcceptanceDeadline(), application.getResultReleasedAt(), decision, null);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public boolean respondToOffer(Long memberId, String applicationNumber, OfferDecisionRequest.Decision decision,
                                  OfferConfirmation confirmation) {
        Long levelId = applications.findProgrammeLevelIdForMember(applicationNumber, memberId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Application not found"));
        programmeLevels.findLockedById(levelId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Programme level not found"));
        Application application = applications.findLockedByApplicationNumberAndMember_Id(applicationNumber, memberId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Application not found"));
        if (application.getStatus() != ApplicationStatus.Success || application.getRespondedAt() != null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This offer cannot be changed");
        }
        LocalDateTime now = LocalDateTime.now(HONG_KONG);
        if (application.getAcceptanceDeadline() == null || !now.isBefore(application.getAcceptanceDeadline())) {
            expire(application, now);
            return false;
        }
        if (confirmation == null || confirmation.decision() != decision
                || !java.util.Objects.equals(application.getAcceptanceDeadline(), confirmation.acceptanceDeadline())
                || !java.util.Objects.equals(application.getResultReleasedAt(), confirmation.resultReleasedAt())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "The offer changed; review it again");
        }
        ApplicationStatus result = decision == OfferDecisionRequest.Decision.Accept
                ? ApplicationStatus.Accepted : ApplicationStatus.Rejected;
        application.setStatus(result);
        application.setRespondedAt(now);
        applications.save(application);
        recordHistory(application, ApplicationStatus.Success, result, ActorRole.ROLE_MEMBER, memberId, null, now);
        return true;
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public boolean extendDeadline(Long adminId, String applicationNumber, LocalDateTime newDeadline, String reason) {
        Admin admin = activeAdmin(adminId);
        Long levelId = applications.findProgrammeLevelId(applicationNumber)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Application not found"));
        programmeLevels.findLockedById(levelId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Programme level not found"));
        Application application = applications.findLockedByApplicationNumber(applicationNumber)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Application not found"));
        LocalDateTime now = LocalDateTime.now(HONG_KONG);
        if (application.getStatus() != ApplicationStatus.Success || application.getRespondedAt() != null
                || application.getAcceptanceDeadline() == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Only an unexpired, unanswered offer can be extended");
        }
        if (!now.isBefore(application.getAcceptanceDeadline())) {
            expire(application, now);
            return false;
        }
        if (reason == null || reason.isBlank() || newDeadline == null
                || !newDeadline.isAfter(application.getAcceptanceDeadline())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A later deadline and reason are required");
        }
        application.setAcceptanceDeadline(newDeadline);
        applications.save(application);
        recordHistory(application, ApplicationStatus.Success, ApplicationStatus.Success, admin.getRole(),
                admin.getId(), reason.trim(), now);
        notifications.notifyOfferDeadlineExtended(application);
        return true;
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void expireOffer(String applicationNumber) {
        Long levelId = applications.findProgrammeLevelId(applicationNumber).orElse(null);
        if (levelId == null) return;
        programmeLevels.findLockedById(levelId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Programme level not found"));
        Application application = applications.findLockedByApplicationNumber(applicationNumber).orElse(null);
        if (application == null || application.getStatus() != ApplicationStatus.Success
                || application.getAcceptanceDeadline() == null) return;
        LocalDateTime now = LocalDateTime.now(HONG_KONG);
        if (!now.isBefore(application.getAcceptanceDeadline())) expire(application, now);
    }

    void expireOffersForLevel(Long levelId, LocalDateTime now) {
        applications.findAllByProgrammeLevel_IdAndStatusAndAcceptanceDeadlineLessThanEqual(
                        levelId, ApplicationStatus.Success, now)
                .forEach(application -> expire(application, now));
    }

    private void expire(Application application, LocalDateTime now) {
        application.setStatus(ApplicationStatus.Rejected);
        application.setRespondedAt(now);
        applications.save(application);
        recordHistory(application, ApplicationStatus.Success, ApplicationStatus.Rejected,
                ActorRole.ROLE_SYSTEM, null, "Offer expired without a response", now);
    }

    private void recordHistory(Application application, ApplicationStatus previous, ApplicationStatus result,
                               ActorRole role, Long actorId, String reason, LocalDateTime changedAt) {
        StatusHistory entry = new StatusHistory();
        entry.setApplication(application);
        entry.setFromStatus(previous);
        entry.setToStatus(result);
        entry.setChangedByRole(role);
        entry.setChangedById(actorId);
        entry.setRemarks(reason);
        entry.setChangedAt(changedAt);
        history.save(entry);
    }

    private void validateResult(ApplicationStatus result) {
        if (result != ApplicationStatus.Success && result != ApplicationStatus.Wait
                && result != ApplicationStatus.Fail) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Result must be Success, Wait or Fail");
        }
    }

    private Admin activeAdmin(Long id) {
        return admins.findById(id).filter(Admin::isActive)
                .filter(admin -> admin.getRole() == ActorRole.ROLE_ADMIN
                        || admin.getRole() == ActorRole.ROLE_SUPER_ADMIN)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "Active admin required"));
    }
}
