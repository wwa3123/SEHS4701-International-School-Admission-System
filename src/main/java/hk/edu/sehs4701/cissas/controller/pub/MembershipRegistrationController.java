package hk.edu.sehs4701.cissas.controller.pub;

import hk.edu.sehs4701.cissas.dto.request.MembershipRegistrationRequest;
import hk.edu.sehs4701.cissas.dto.request.MembershipEmailRequest;
import hk.edu.sehs4701.cissas.dto.request.VerifyMembershipEmailRequest;
import hk.edu.sehs4701.cissas.dto.response.MembershipRegistrationResponse;
import hk.edu.sehs4701.cissas.service.MembershipRegistrationService;
import hk.edu.sehs4701.cissas.service.OtcService;
import hk.edu.sehs4701.cissas.service.RegistrationRateLimitService;
import jakarta.validation.Valid;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import java.sql.SQLException;

@RestController
@RequestMapping("/api/public/membership")
public class MembershipRegistrationController {
    private static final int MYSQL_DUPLICATE_KEY_ERROR_CODE = 1062;

    private final MembershipRegistrationService membershipRegistrationService;
    private final RegistrationRateLimitService registrationRateLimitService;

    public MembershipRegistrationController(
            MembershipRegistrationService membershipRegistrationService,
            RegistrationRateLimitService registrationRateLimitService) {
        this.membershipRegistrationService = membershipRegistrationService;
        this.registrationRateLimitService = registrationRateLimitService;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public MembershipRegistrationResponse register(
            @Valid @RequestBody MembershipRegistrationRequest request,
            HttpServletRequest servletRequest) {
        registrationRateLimitService.checkAllowed(registrationRateLimitService.resolveClientAddress(servletRequest));
        try {
            membershipRegistrationService.register(request);
        } catch (OtcService.ResendCooldownException ignored) {
            // Return the same response for pending and unknown email addresses to avoid revealing whether the email is already registered.
        } catch (DataIntegrityViolationException ex) {
            // If the exception is a duplicate key violation, we return a generic accepted response to avoid revealing whether the email is already registered.
            if (!isDuplicateKeyViolation(ex)) {
                throw ex;
            }
        } catch (ResponseStatusException ex) {
            if (ex.getStatusCode() != HttpStatus.CONFLICT) {
                throw ex;
            }
        }
        return acceptedResponse(request.email());
    }

    @PostMapping("/verify-email")
    public MembershipRegistrationResponse verifyEmail(@Valid @RequestBody VerifyMembershipEmailRequest request) {
        try {
            membershipRegistrationService.verifyEmail(request.email(), request.code());
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unable to verify membership", ex);
        }
        return new MembershipRegistrationResponse(
                request.email().trim(),
                "ACTIVE",
                "Membership registration completed"
        );
    }

    @PostMapping("/resend-verification")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public MembershipRegistrationResponse resendVerification(@Valid @RequestBody MembershipEmailRequest request) {
        try {
            membershipRegistrationService.resendVerification(request.email());
        } catch (OtcService.ResendCooldownException ignored) {
            // Return the same response for pending and unknown email addresses.
        }
        return acceptedResponse(request.email());
    }

    private MembershipRegistrationResponse acceptedResponse(String email) {
        return new MembershipRegistrationResponse(
                email.trim(),
                "ACCEPTED",
                "If this email can be registered, verification instructions will be sent"
        );
    }
    private boolean isDuplicateKeyViolation(Throwable exception) {
        for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
            if (cause instanceof SQLException sqlException && sqlException.getErrorCode() == MYSQL_DUPLICATE_KEY_ERROR_CODE) {
                return true;
            }
        }
        return false;
    }
}
