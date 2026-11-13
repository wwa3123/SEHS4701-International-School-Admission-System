package hk.edu.sehs4701.cissas.controller.web;

import hk.edu.sehs4701.cissas.dto.request.MembershipEmailRequest;
import hk.edu.sehs4701.cissas.dto.request.MembershipRegistrationRequest;
import hk.edu.sehs4701.cissas.dto.request.VerifyMembershipEmailRequest;
import hk.edu.sehs4701.cissas.enums.IdentityDocumentType;
import hk.edu.sehs4701.cissas.service.MembershipRegistrationService;
import hk.edu.sehs4701.cissas.service.OtcService;
import hk.edu.sehs4701.cissas.service.RateLimitService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.sql.SQLException;
import java.time.Instant;
import java.util.Locale;

@Controller
@RequestMapping("/membership")
public class MembershipPageController {
    private static final String VERIFIED_EMAIL = "registrationVerifiedEmail";
    private static final String VERIFIED_UNTIL = "registrationVerifiedUntil";
    private static final String ACCEPTED = "If this email can be registered, verification instructions will be sent";
    private final MembershipRegistrationService registrations;
    private final RateLimitService limits;

    public MembershipPageController(MembershipRegistrationService registrations, RateLimitService limits) {
        this.registrations = registrations;
        this.limits = limits;
    }

    @ModelAttribute("documentTypes")
    IdentityDocumentType[] documentTypes() {
        return IdentityDocumentType.values();
    }

    @GetMapping("/register")
    public String register(Model model) {
        model.addAttribute("emailForm", new MembershipEmailRequest(""));
        return "auth/registration";
    }

    @PostMapping("/register")
    public String register(@Valid @ModelAttribute("emailForm") MembershipEmailRequest form, BindingResult errors,
                           HttpServletRequest request, HttpServletResponse response, Model model,
                           RedirectAttributes redirect) {
        if (errors.hasErrors()) {
            response.setStatus(400);
            return "auth/registration";
        }
        try {
            limits.checkAllowed(limits.resolveClientAddress(request));
            try {
                registrations.register(form);
            } catch (OtcService.ResendCooldownException ignored) {
                // Keep registered, pending and unknown addresses indistinguishable.
            } catch (DataIntegrityViolationException ex) {
                if (!isDuplicateKeyViolation(ex)) throw ex;
            } catch (ResponseStatusException ex) {
                if (ex.getStatusCode() != HttpStatus.CONFLICT) throw ex;
            }
        } catch (ResponseStatusException ex) {
            if (ex.getStatusCode() != HttpStatus.TOO_MANY_REQUESTS) throw ex;
            return error(ex, response, model, "auth/registration");
        }
        return accepted(form.email(), redirect);
    }

    @GetMapping("/verify-email")
    public String verify(@RequestParam(defaultValue = "") String email, Model model) {
        model.addAttribute("verificationForm", new VerifyMembershipEmailRequest(email, ""));
        return "auth/registration-verify";
    }

    @PostMapping("/verify-email")
    public String verify(@Valid @ModelAttribute("verificationForm") VerifyMembershipEmailRequest form,
                         BindingResult errors, HttpServletRequest request, HttpServletResponse response, Model model) {
        clearProof(request.getSession(false));
        if (errors.hasErrors()) {
            response.setStatus(400);
            return "auth/registration-verify";
        }
        try {
            limits.checkAllowed("registration-verify:" + limits.resolveClientAddress(request));
            registrations.verifyEmail(form.email(), form.code());
        } catch (IllegalArgumentException ex) {
            response.setStatus(400);
            model.addAttribute("error", "Unable to verify membership");
            return "auth/registration-verify";
        } catch (ResponseStatusException ex) {
            if (ex.getStatusCode() != HttpStatus.TOO_MANY_REQUESTS) throw ex;
            return error(ex, response, model, "auth/registration-verify");
        }
        HttpSession session = request.getSession(true);
        request.changeSessionId();
        session.setAttribute(VERIFIED_EMAIL, form.email().trim().toLowerCase(Locale.ROOT));
        session.setAttribute(VERIFIED_UNTIL, Instant.now().plusSeconds(900));
        return "redirect:/membership/complete-registration";
    }

    @PostMapping("/resend-verification")
    public String resend(@Valid @ModelAttribute("verificationForm") MembershipEmailRequest form, BindingResult errors,
                         HttpServletRequest request, HttpServletResponse response, Model model,
                         RedirectAttributes redirect) {
        if (errors.hasErrors()) {
            response.setStatus(400);
            return "auth/registration-verify";
        }
        try {
            limits.checkAllowed("registration-resend:" + limits.resolveClientAddress(request));
            try {
                registrations.resendVerification(form.email());
            } catch (OtcService.ResendCooldownException ignored) {
                // The cooldown must not reveal whether the address is pending.
            }
        } catch (ResponseStatusException ex) {
            if (ex.getStatusCode() != HttpStatus.TOO_MANY_REQUESTS) throw ex;
            return error(ex, response, model, "auth/registration-verify");
        }
        return accepted(form.email(), redirect);
    }

    @GetMapping("/complete-registration")
    public String complete(HttpServletRequest request, Model model, RedirectAttributes redirect) {
        String email = verifiedEmail(request);
        if (email == null) {
            redirect.addFlashAttribute("error", "Email verification is required");
            return "redirect:/membership/verify-email";
        }
        model.addAttribute("registrationForm",
                new MembershipRegistrationRequest("", "", email, "", null, "", "", ""));
        return "auth/registration-details";
    }

    @PostMapping("/complete-registration")
    public String complete(@Valid @ModelAttribute("registrationForm") MembershipRegistrationRequest form,
                           BindingResult errors, HttpServletRequest request, HttpServletResponse response, Model model,
                           RedirectAttributes redirect) {
        String email = verifiedEmail(request);
        if (email == null) {
            response.setStatus(403);
            model.addAttribute("verificationForm", new VerifyMembershipEmailRequest("", ""));
            model.addAttribute("error", "Email verification is required");
            return "auth/registration-verify";
        }
        if (errors.hasErrors()) {
            response.setStatus(400);
            return "auth/registration-details";
        }
        try {
            registrations.completeRegistration(email, form);
        } catch (DataIntegrityViolationException ex) {
            if (!isDuplicateKeyViolation(ex)) throw ex;
            response.setStatus(409);
            model.addAttribute("error", "Identity document is already linked to an account");
            return "auth/registration-details";
        } catch (ResponseStatusException ex) {
            int status = ex.getStatusCode().value();
            if (status != 400 && status != 403 && status != 409) throw ex;
            return error(ex, response, model, "auth/registration-details");
        }
        clearProof(request.getSession(false));
        redirect.addFlashAttribute("message", "Membership registration completed. You can now sign in.");
        return "redirect:/membership/success";
    }

    @GetMapping("/success")
    public String success() {
        return "auth/registration-success";
    }

    private String accepted(String email, RedirectAttributes redirect) {
        redirect.addAttribute("email", email.trim());
        redirect.addFlashAttribute("message", ACCEPTED);
        return "redirect:/membership/verify-email";
    }

    private String verifiedEmail(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null && session.getAttribute(VERIFIED_EMAIL) instanceof String email
                && session.getAttribute(VERIFIED_UNTIL) instanceof Instant expiry && Instant.now().isBefore(expiry)) {
            return email;
        }
        clearProof(session);
        return null;
    }

    private void clearProof(HttpSession session) {
        if (session != null) {
            session.removeAttribute(VERIFIED_EMAIL);
            session.removeAttribute(VERIFIED_UNTIL);
        }
    }

    private String error(ResponseStatusException ex, HttpServletResponse response, Model model, String view) {
        response.setStatus(ex.getStatusCode().value());
        model.addAttribute("error", ex.getReason());
        return view;
    }

    private boolean isDuplicateKeyViolation(Throwable exception) {
        for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
            if (cause instanceof SQLException sql && sql.getErrorCode() == 1062) return true;
        }
        return false;
    }
}
