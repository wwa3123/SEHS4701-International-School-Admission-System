package hk.edu.sehs4701.cissas.controller.api.admin;

import hk.edu.sehs4701.cissas.enums.ActorRole;
import hk.edu.sehs4701.cissas.service.IdentityReviewService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/admin/identity-reviews")
public class IdentityReviewController {
    private final IdentityReviewService service;

    public IdentityReviewController(IdentityReviewService service) {
        this.service = service;
    }

    public record ReviewRequest(@NotBlank String flaggedApplicationNumber,
                                @NotBlank String candidateApplicationNumber,
                                @NotNull Boolean samePerson) {}

    @GetMapping
    public ResponseEntity<IdentityReviewService.CandidatePage> pending(@RequestParam(required = false) String cursor,
                                                       @RequestParam(defaultValue = "25") int size,
                                                       HttpServletRequest request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.pending(adminId(request), cursor, size));
    }

    @GetMapping("/applications/{applicationNumber}")
    public ResponseEntity<IdentityReviewService.ApplicationEvidence> evidence(@PathVariable String applicationNumber,
                                                               HttpServletRequest request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.evidence(adminId(request), applicationNumber));
    }

    @GetMapping("/history")
    public ResponseEntity<IdentityReviewService.HistoryPage> history(@RequestParam(required = false) String cursor,
                                                                     @RequestParam(defaultValue = "25") int size,
                                                                     HttpServletRequest request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.history(adminId(request), cursor, size));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public void review(@Valid @RequestBody ReviewRequest review, HttpServletRequest request) {
        service.review(adminId(request), review.flaggedApplicationNumber(),
                review.candidateApplicationNumber(), review.samePerson());
    }

    private Long adminId(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Admin login required");
        }
        if (!ActorRole.ROLE_ADMIN.name().equals(session.getAttribute("authenticatedUserRole"))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Admin access required");
        }
        Object id = session.getAttribute("authenticatedUserId");
        if (!(id instanceof Long adminId)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Admin login required");
        }
        return adminId;
    }
}
