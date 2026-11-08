package hk.edu.sehs4701.cissas.controller.api.pub;

import hk.edu.sehs4701.cissas.dto.request.MembershipRegistrationRequest;
import hk.edu.sehs4701.cissas.dto.request.MembershipEmailRequest;
import hk.edu.sehs4701.cissas.dto.request.VerifyMembershipEmailRequest;
import hk.edu.sehs4701.cissas.dto.response.MembershipRegistrationResponse;
import hk.edu.sehs4701.cissas.service.MembershipRegistrationService;
import hk.edu.sehs4701.cissas.service.OtcService;
import hk.edu.sehs4701.cissas.service.RateLimitService;
import jakarta.validation.Valid;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Locale;

@RestController
@RequestMapping("/api/public/membership")
public class MembershipRegistrationController {
    private static final int MYSQL_DUPLICATE_KEY_ERROR_CODE = 1062;
    private static final String VERIFIED_EMAIL = "registrationVerifiedEmail";
    private static final String VERIFIED_UNTIL = "registrationVerifiedUntil";

    private final MembershipRegistrationService membershipRegistrationService;
    private final RateLimitService rateLimitService;

    public MembershipRegistrationController(
            MembershipRegistrationService membershipRegistrationService,
            RateLimitService rateLimitService) {
        this.membershipRegistrationService = membershipRegistrationService;
        this.rateLimitService = rateLimitService;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public MembershipRegistrationResponse register(
            @Valid @RequestBody MembershipEmailRequest request,
            HttpServletRequest servletRequest) {
        rateLimitService.checkAllowed(rateLimitService.resolveClientAddress(servletRequest));
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
    public MembershipRegistrationResponse verifyEmail(
            @Valid @RequestBody VerifyMembershipEmailRequest request, HttpServletRequest servletRequest) {
        HttpSession previous = servletRequest.getSession(false);
        if (previous != null) {
            previous.removeAttribute(VERIFIED_EMAIL);
            previous.removeAttribute(VERIFIED_UNTIL);
        }
        rateLimitService.checkAllowed("registration-verify:" + rateLimitService.resolveClientAddress(servletRequest));
        try {
            membershipRegistrationService.verifyEmail(request.email(), request.code());
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unable to verify membership", ex);
        }
        HttpSession session = servletRequest.getSession(true);
        servletRequest.changeSessionId();
        session.setAttribute(VERIFIED_EMAIL, request.email().trim().toLowerCase(Locale.ROOT));
        session.setAttribute(VERIFIED_UNTIL, Instant.now().plusSeconds(900));
        return new MembershipRegistrationResponse(
                request.email().trim(),
                "EMAIL_VERIFIED",
                "Email verified; complete your membership registration"
        );
    }

    @PostMapping("/complete-registration")
    public MembershipRegistrationResponse completeRegistration(
            @Valid @RequestBody MembershipRegistrationRequest request, HttpServletRequest servletRequest) {
        HttpSession session = servletRequest.getSession(false);
        if (session == null || !(session.getAttribute(VERIFIED_EMAIL) instanceof String email)
                || !(session.getAttribute(VERIFIED_UNTIL) instanceof Instant expiry)
                || !Instant.now().isBefore(expiry)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Email verification is required");
        }
        try {
            membershipRegistrationService.completeRegistration(email, request);
        } catch (DataIntegrityViolationException ex) {
            if (!isDuplicateKeyViolation(ex)) {
                throw ex;
            }
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Identity document is already linked to an account", ex);
        }
        session.removeAttribute(VERIFIED_EMAIL);
        session.removeAttribute(VERIFIED_UNTIL);
        return new MembershipRegistrationResponse(request.email().trim(), "ACTIVE", "Membership registration completed");
    }

    @PostMapping("/resend-verification")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public MembershipRegistrationResponse resendVerification(
            @Valid @RequestBody MembershipEmailRequest request, HttpServletRequest servletRequest) {
        rateLimitService.checkAllowed("registration-resend:" + rateLimitService.resolveClientAddress(servletRequest));
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
