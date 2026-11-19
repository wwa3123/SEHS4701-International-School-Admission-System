package hk.edu.sehs4701.cissas.controller.api.pub;

import hk.edu.sehs4701.cissas.dto.request.PasswordResetCodeRequest;
import hk.edu.sehs4701.cissas.dto.request.PasswordResetConfirmationRequest;
import hk.edu.sehs4701.cissas.service.MemberPasswordResetService;
import hk.edu.sehs4701.cissas.service.RateLimitService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/public/auth/password-reset")
public class PasswordResetController {
    private final MemberPasswordResetService resets;
    private final RateLimitService limits;

    public PasswordResetController(MemberPasswordResetService resets, RateLimitService limits) {
        this.resets = resets;
        this.limits = limits;
    }

    @PostMapping("/request")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void request(@Valid @RequestBody PasswordResetCodeRequest form, HttpServletRequest request) {
        limits.checkAllowed("password-reset-request:" + limits.resolveClientAddress(request));
        resets.requestCode(form.email());
    }

    @PostMapping("/confirm")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void confirm(@Valid @RequestBody PasswordResetConfirmationRequest form, HttpServletRequest request) {
        limits.checkAllowed("password-reset-confirm:" + limits.resolveClientAddress(request));
        try {
            resets.resetPassword(form.email(), form.code(), form.newPassword());
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid or expired reset code");
        }
    }
}
