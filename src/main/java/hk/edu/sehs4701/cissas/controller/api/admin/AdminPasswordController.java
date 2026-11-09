package hk.edu.sehs4701.cissas.controller.api.admin;

import hk.edu.sehs4701.cissas.dto.internal.PasswordSession;
import hk.edu.sehs4701.cissas.dto.request.PasswordChangeRequest;
import hk.edu.sehs4701.cissas.service.AdminAccountService;
import hk.edu.sehs4701.cissas.service.RateLimitService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin")
public class AdminPasswordController {
    private final AdminAccountService accounts;
    private final RateLimitService rateLimits;

    public AdminPasswordController(AdminAccountService accounts, RateLimitService rateLimits) {
        this.accounts = accounts;
        this.rateLimits = rateLimits;
    }

    @PutMapping("/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void changePassword(@RequestBody PasswordChangeRequest change,
                               HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        PasswordSession authenticated = accounts.requirePasswordSession(request);
        rateLimits.checkAllowed("admin-password-account:" + authenticated.id() + ":" + authenticated.credentialVersion(),
                "Too many password attempts");
        accounts.changePassword(request, change);
        session.invalidate();
    }
}
