package hk.edu.sehs4701.cissas.controller.web;

import hk.edu.sehs4701.cissas.dto.request.SkipFailedEmailRequest;
import hk.edu.sehs4701.cissas.service.AdminAccountService;
import hk.edu.sehs4701.cissas.service.EmailService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/admin/emails")
@Transactional(isolation = Isolation.READ_COMMITTED)
public class AdminEmailPageController {
    private final AdminAccountService accounts;
    private final EmailService emails;

    public AdminEmailPageController(AdminAccountService accounts, EmailService emails) {
        this.accounts = accounts;
        this.emails = emails;
    }

    @GetMapping("/failed")
    public String failed(@RequestParam(defaultValue = "0") int page, HttpServletRequest request, Model model) {
        accounts.requireAdminForUpdate(request);
        model.addAttribute("emails", emails.failedApplicationEmails(page));
        return "admin/failed-emails";
    }

    @PostMapping("/{id}/resend")
    public String resend(@PathVariable Long id, HttpServletRequest request, RedirectAttributes redirect) {
        accounts.requireAdminForUpdate(request);
        emails.manualResend(id);
        redirect.addFlashAttribute("message", "Email queued for delivery.");
        return "redirect:/admin/emails/failed";
    }

    @GetMapping("/{id}/skip")
    public String skip(@PathVariable Long id, HttpServletRequest request, Model model) {
        accounts.requireAdminForUpdate(request);
        model.addAttribute("id", id);
        model.addAttribute("skipForm", new SkipFailedEmailRequest(""));
        return "admin/skip-email";
    }

    @PostMapping("/{id}/skip")
    public String skip(@PathVariable Long id, @Valid @ModelAttribute("skipForm") SkipFailedEmailRequest form,
                       BindingResult errors, HttpServletRequest request, HttpServletResponse response,
                       Model model, RedirectAttributes redirect) {
        Long adminId = accounts.requireAdminForUpdate(request);
        model.addAttribute("id", id);
        if (errors.hasErrors()) {
            response.setStatus(400);
            return "admin/skip-email";
        }
        emails.skipFailedApplicationEmail(id, adminId, form.reason());
        redirect.addFlashAttribute("message", "Failed email skipped; the next email was queued.");
        return "redirect:/admin/emails/failed";
    }

    @ExceptionHandler({IllegalArgumentException.class, IllegalStateException.class})
    public String emailError(RuntimeException ex, HttpServletResponse response, Model model) {
        response.setStatus(ex instanceof IllegalArgumentException ? 400 : 409);
        model.addAttribute("error", ex.getMessage());
        return "admin/action-error";
    }
}
