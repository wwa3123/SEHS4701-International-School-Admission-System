package hk.edu.sehs4701.cissas.controller.api.admin;

import hk.edu.sehs4701.cissas.service.AdminAccountService;
import hk.edu.sehs4701.cissas.service.EmailService;
import hk.edu.sehs4701.cissas.dto.response.FailedApplicationEmail;
import hk.edu.sehs4701.cissas.dto.request.SkipFailedEmailRequest;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.data.domain.Slice;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/admin/emails")
public class EmailRecoveryController {
    private final AdminAccountService accounts;
    private final EmailService emails;

    public EmailRecoveryController(AdminAccountService accounts, EmailService emails) {
        this.accounts = accounts;
        this.emails = emails;
    }

    @GetMapping("/failed")
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public ResponseEntity<Slice<FailedApplicationEmail>> failed(@RequestParam(defaultValue = "0") int page,
                                                                HttpServletRequest request) {
        accounts.requireAdminForUpdate(request);
        if (page < 0) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Page must not be negative");
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(emails.failedApplicationEmails(page));
    }

    @PostMapping("/{id}/resend")
    @ResponseStatus(HttpStatus.ACCEPTED)
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void resend(@PathVariable Long id, HttpServletRequest request) {
        accounts.requireAdminForUpdate(request);
        try {
            emails.manualResend(id);
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, ex.getMessage(), ex);
        } catch (IllegalStateException ex) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, ex.getMessage(), ex);
        }
    }

    @PostMapping("/{id}/skip")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void skip(@PathVariable Long id, @Valid @RequestBody SkipFailedEmailRequest body,
                     HttpServletRequest request) {
        Long adminId = accounts.requireAdminForUpdate(request);
        try {
            emails.skipFailedApplicationEmail(id, adminId, body.reason());
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, ex.getMessage(), ex);
        } catch (IllegalStateException ex) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, ex.getMessage(), ex);
        }
    }
}
