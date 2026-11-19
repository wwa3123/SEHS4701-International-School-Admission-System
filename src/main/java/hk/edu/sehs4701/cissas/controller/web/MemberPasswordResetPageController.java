package hk.edu.sehs4701.cissas.controller.web;

import hk.edu.sehs4701.cissas.dto.request.PasswordResetCodeRequest;
import hk.edu.sehs4701.cissas.dto.request.PasswordResetConfirmationRequest;
import hk.edu.sehs4701.cissas.service.MemberPasswordResetService;
import hk.edu.sehs4701.cissas.service.RateLimitService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class MemberPasswordResetPageController {
    private static final String VIEW = "auth/password-reset";
    private static final String REQUEST_LIMIT = "password-reset-request:";
    private static final String CONFIRM_LIMIT = "password-reset-confirm:";

    private final MemberPasswordResetService resets;
    private final RateLimitService limits;

    public MemberPasswordResetPageController(MemberPasswordResetService resets, RateLimitService limits) {
        this.resets = resets;
        this.limits = limits;
    }

    @GetMapping("/password-reset")
    public String page(Model model) {
        forms(model);
        return VIEW;
    }

    @PostMapping("/password-reset/request")
    public String request(@Valid @ModelAttribute("requestForm") PasswordResetCodeRequest form,
                          BindingResult errors, HttpServletRequest request, HttpServletResponse response,
                          Model model, RedirectAttributes redirect) {
        if (errors.hasErrors()) {
            response.setStatus(HttpStatus.BAD_REQUEST.value());
            model.addAttribute("confirmationForm", new PasswordResetConfirmationRequest("", "", ""));
            return VIEW;
        }
        try {
            limits.checkAllowed(REQUEST_LIMIT + limits.resolveClientAddress(request));
            resets.requestCode(form.email());
        } catch (ResponseStatusException exception) {
            if (exception.getStatusCode() != HttpStatus.TOO_MANY_REQUESTS) throw exception;
            return error(response, model, HttpStatus.TOO_MANY_REQUESTS, exception.getReason(),
                    form, new PasswordResetConfirmationRequest("", "", ""));
        }
        redirect.addFlashAttribute("message",
                "If this email belongs to an active member, password reset instructions will be sent.");
        return "redirect:/password-reset";
    }

    @PostMapping("/password-reset/confirm")
    public String confirm(@Valid @ModelAttribute("confirmationForm") PasswordResetConfirmationRequest form,
                          BindingResult errors, HttpServletRequest request, HttpServletResponse response,
                          Model model) {
        if (errors.hasErrors()) {
            response.setStatus(HttpStatus.BAD_REQUEST.value());
            model.addAttribute("requestForm", new PasswordResetCodeRequest(""));
            model.addAttribute("confirmationForm", safeForm(form));
            return VIEW;
        }
        try {
            limits.checkAllowed(CONFIRM_LIMIT + limits.resolveClientAddress(request));
            resets.resetPassword(form.email(), form.code(), form.newPassword());
        } catch (IllegalArgumentException exception) {
            return error(response, model, HttpStatus.BAD_REQUEST, "Invalid or expired reset code.",
                    new PasswordResetCodeRequest(""), safeForm(form));
        } catch (ResponseStatusException exception) {
            if (exception.getStatusCode() == HttpStatus.TOO_MANY_REQUESTS) {
                return error(response, model, HttpStatus.TOO_MANY_REQUESTS, exception.getReason(),
                        new PasswordResetCodeRequest(""), safeForm(form));
            }
            if (exception.getStatusCode() != HttpStatus.BAD_REQUEST) throw exception;
            return error(response, model, HttpStatus.BAD_REQUEST, exception.getReason(),
                    new PasswordResetCodeRequest(""), safeForm(form));
        }
        return "redirect:/login?passwordReset";
    }

    private String error(HttpServletResponse response, Model model, HttpStatus status, String message,
                         PasswordResetCodeRequest requestForm,
                         PasswordResetConfirmationRequest confirmationForm) {
        response.setStatus(status.value());
        model.addAttribute("error", message);
        model.addAttribute("requestForm", requestForm);
        model.addAttribute("confirmationForm", confirmationForm);
        return VIEW;
    }

    private void forms(Model model) {
        model.addAttribute("requestForm", new PasswordResetCodeRequest(""));
        model.addAttribute("confirmationForm", new PasswordResetConfirmationRequest("", "", ""));
    }

    private PasswordResetConfirmationRequest safeForm(PasswordResetConfirmationRequest form) {
        return new PasswordResetConfirmationRequest(form.email(), "", "");
    }
}
