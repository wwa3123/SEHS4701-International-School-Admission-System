package hk.edu.sehs4701.cissas.controller.api.admin;

import hk.edu.sehs4701.cissas.dto.request.AccountRequest;
import hk.edu.sehs4701.cissas.dto.request.PasswordResetRequest;
import hk.edu.sehs4701.cissas.dto.response.AccountResponse;
import hk.edu.sehs4701.cissas.service.AdminAccountService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/super-admin/admissions-officers")
public class AdmissionsOfficerController {
    private final AdminAccountService accounts;

    public AdmissionsOfficerController(AdminAccountService accounts) {
        this.accounts = accounts;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public AccountResponse create(@RequestBody AccountRequest account, HttpServletRequest request) {
        accounts.requireSuperAdminForUpdate(request);
        return accounts.createOfficer(account);
    }

    @GetMapping
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public ResponseEntity<List<AccountResponse>> list(HttpServletRequest request) {
        accounts.requireSuperAdminForUpdate(request);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(accounts.listOfficers());
    }

    @DeleteMapping("/{id}")
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public AccountResponse deactivate(@PathVariable Long id, HttpServletRequest request) {
        accounts.requireSuperAdminForUpdate(request);
        return accounts.deactivateOfficer(id);
    }

    @PutMapping("/{id}/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void resetPassword(@PathVariable Long id, @RequestBody PasswordResetRequest reset,
                              HttpServletRequest request) {
        accounts.requireSuperAdminForUpdate(request);
        accounts.resetOfficerPassword(id, reset);
    }
}
