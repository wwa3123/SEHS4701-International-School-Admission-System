package hk.edu.sehs4701.cissas.controller.api.admin;

import hk.edu.sehs4701.cissas.service.AdminAccountService;
import hk.edu.sehs4701.cissas.service.IdentityReviewService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/identity-reviews")
public class IdentityReviewController {
    private final IdentityReviewService service;
    private final AdminAccountService accounts;

    public IdentityReviewController(IdentityReviewService service, AdminAccountService accounts) {
        this.service = service;
        this.accounts = accounts;
    }

    public record ReviewRequest(@NotBlank String flaggedApplicationNumber,
                                @NotBlank String candidateApplicationNumber,
                                @NotNull Boolean samePerson) {}

    @GetMapping
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public ResponseEntity<IdentityReviewService.CandidatePage> pending(@RequestParam(required = false) String cursor,
                                                       @RequestParam(defaultValue = "25") int size,
                                                       HttpServletRequest request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.pending(adminId(request), cursor, size));
    }

    @GetMapping("/applications/{applicationNumber}")
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public ResponseEntity<IdentityReviewService.ApplicationEvidence> evidence(@PathVariable String applicationNumber,
                                                               HttpServletRequest request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.evidence(adminId(request), applicationNumber));
    }

    @GetMapping("/history")
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public ResponseEntity<IdentityReviewService.HistoryPage> history(@RequestParam(required = false) String cursor,
                                                                     @RequestParam(defaultValue = "25") int size,
                                                                     HttpServletRequest request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.history(adminId(request), cursor, size));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void review(@Valid @RequestBody ReviewRequest review, HttpServletRequest request) {
        service.review(accounts.requireAdminForUpdate(request), review.flaggedApplicationNumber(),
                review.candidateApplicationNumber(), review.samePerson());
    }

    private Long adminId(HttpServletRequest request) {
        return accounts.requireAdminForUpdate(request);
    }
}
