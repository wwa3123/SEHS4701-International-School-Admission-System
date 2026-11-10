package hk.edu.sehs4701.cissas.controller.api.admin;

import hk.edu.sehs4701.cissas.dto.request.ExtendOfferDeadlineRequest;
import hk.edu.sehs4701.cissas.dto.request.ApplicationDuplicateReviewRequest;
import hk.edu.sehs4701.cissas.dto.request.SetApplicationResultRequest;
import hk.edu.sehs4701.cissas.dto.response.ApplicationEvidence;
import hk.edu.sehs4701.cissas.dto.response.ApplicationSummary;
import hk.edu.sehs4701.cissas.dto.response.CandidatePage;
import hk.edu.sehs4701.cissas.dto.response.HistoryPage;
import hk.edu.sehs4701.cissas.dto.response.AdminApplication;
import hk.edu.sehs4701.cissas.enums.ApplicationStatus;
import hk.edu.sehs4701.cissas.service.AdminAccountService;
import hk.edu.sehs4701.cissas.service.ApplicationResultService;
import hk.edu.sehs4701.cissas.service.IdentityReviewService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.data.domain.Slice;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/applications")
public class ApplicationResultController {
    private final ApplicationResultService results;
    private final AdminAccountService accounts;
    private final IdentityReviewService duplicates;

    public ApplicationResultController(ApplicationResultService results, AdminAccountService accounts,
                                       IdentityReviewService duplicates) {
        this.results = results;
        this.accounts = accounts;
        this.duplicates = duplicates;
    }

    @PutMapping("/{applicationNumber}/result")
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public ResponseEntity<Void> setResult(@PathVariable String applicationNumber,
                          @Valid @RequestBody SetApplicationResultRequest request,
                          HttpServletRequest servletRequest) {
        if (!results.setResult(accounts.requireAdminForUpdate(servletRequest), applicationNumber,
                request.result(), request.reason())) {
            return ResponseEntity.status(HttpStatus.CONFLICT).build();
        }
        return ResponseEntity.noContent().build();
    }

    @GetMapping
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public ResponseEntity<Slice<AdminApplication>> applications(
            @RequestParam(required = false) Long programmeLevelId,
            @RequestParam(required = false) ApplicationStatus status,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "25") int size,
            HttpServletRequest request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(
                results.listApplications(accounts.requireAdminForUpdate(request), programmeLevelId, status, page, size));
    }

    @GetMapping("/waiting-list")
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public ResponseEntity<List<ApplicationSummary>> waitingList(
            @RequestParam Long programmeLevelId, HttpServletRequest request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(results.waitingList(accounts.requireAdminForUpdate(request), programmeLevelId));
    }

    @PutMapping("/{applicationNumber}/acceptance-deadline")
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public ResponseEntity<Void> extendDeadline(@PathVariable String applicationNumber,
                               @Valid @RequestBody ExtendOfferDeadlineRequest request,
                               HttpServletRequest servletRequest) {
        if (!results.extendDeadline(accounts.requireAdminForUpdate(servletRequest), applicationNumber,
                request.acceptanceDeadline(), request.reason())) {
            return ResponseEntity.status(HttpStatus.CONFLICT).build();
        }
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/possible-duplicates")
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public ResponseEntity<CandidatePage> possibleDuplicates(@RequestParam(required = false) String cursor,
                                                             @RequestParam(defaultValue = "25") int size,
                                                             HttpServletRequest request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(
                duplicates.pending(accounts.requireAdminForUpdate(request), cursor, size));
    }

    @GetMapping("/possible-duplicates/history")
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public ResponseEntity<HistoryPage> duplicateHistory(@RequestParam(required = false) String cursor,
                                                         @RequestParam(defaultValue = "25") int size,
                                                         HttpServletRequest request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(
                duplicates.history(accounts.requireAdminForUpdate(request), cursor, size));
    }

    @GetMapping("/possible-duplicates/applications/{applicationNumber}")
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public ResponseEntity<ApplicationEvidence> duplicateEvidence(@PathVariable String applicationNumber,
                                                                  HttpServletRequest request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(
                duplicates.evidence(accounts.requireAdminForUpdate(request), applicationNumber));
    }

    @PostMapping("/possible-duplicates")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void reviewDuplicate(@Valid @RequestBody ApplicationDuplicateReviewRequest review,
                                HttpServletRequest request) {
        duplicates.review(accounts.requireAdminForUpdate(request), review.duplicateApplicationNumber(),
                review.matchingApplicationNumber(), review.samePerson());
    }
}
