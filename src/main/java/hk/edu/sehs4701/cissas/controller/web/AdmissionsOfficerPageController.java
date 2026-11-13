package hk.edu.sehs4701.cissas.controller.web;

import hk.edu.sehs4701.cissas.dto.request.AccountRequest;
import hk.edu.sehs4701.cissas.dto.request.PasswordResetRequest;
import hk.edu.sehs4701.cissas.service.AdminAccountService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Officer DTO pages replace /api/super-admin/admissions-officers.
 * All mutations use POST and redirect to the account list.
 */
@Controller
@RequestMapping("/super-admin/admissions-officers")
@Transactional(isolation = Isolation.READ_COMMITTED)
public class AdmissionsOfficerPageController {
    private final AdminAccountService accounts;

    public AdmissionsOfficerPageController(AdminAccountService accounts) {
        this.accounts = accounts;
    }

    @GetMapping
    public String list(HttpServletRequest request, HttpServletResponse response, Model model) {
        guard(request, response);
        model.addAttribute("officers", accounts.listOfficers());
        return "super-admin/officers";
    }

    @GetMapping("/new")
    public String createForm(HttpServletRequest request, HttpServletResponse response, Model model) {
        guard(request, response);
        model.addAttribute("accountForm", new AccountRequest("", "", "", ""));
        return "super-admin/officer-form";
    }

    @PostMapping
    public String create(@Valid @ModelAttribute("accountForm") AccountRequest form, BindingResult errors,
                         HttpServletRequest request, HttpServletResponse response, Model model,
                         RedirectAttributes redirect) {
        guard(request, response);
        remember(request, model, "super-admin/officer-form");
        if (errors.hasErrors()) {
            response.setStatus(400);
            return "super-admin/officer-form";
        }
        accounts.createOfficer(form);
        redirect.addFlashAttribute("message", "Admissions officer created. A password change is required at login.");
        return "redirect:/super-admin/admissions-officers";
    }

    @GetMapping("/{id}/password")
    public String passwordForm(@PathVariable Long id, HttpServletRequest request,
                               HttpServletResponse response, Model model) {
        guard(request, response);
        model.addAttribute("officerId", id);
        model.addAttribute("passwordForm", new PasswordResetRequest(""));
        return "super-admin/officer-password";
    }

    @PostMapping("/{id}/password")
    public String resetPassword(@PathVariable Long id,
                                @Valid @ModelAttribute("passwordForm") PasswordResetRequest form, BindingResult errors,
                                HttpServletRequest request, HttpServletResponse response, Model model,
                                RedirectAttributes redirect) {
        guard(request, response);
        model.addAttribute("officerId", id);
        remember(request, model, "super-admin/officer-password");
        if (errors.hasErrors()) {
            response.setStatus(400);
            return "super-admin/officer-password";
        }
        accounts.resetOfficerPassword(id, form);
        redirect.addFlashAttribute("message", "Password reset. The officer must change it at next login.");
        return "redirect:/super-admin/admissions-officers";
    }

    @PostMapping("/{id}/deactivate")
    public String deactivate(@PathVariable Long id, HttpServletRequest request, HttpServletResponse response,
                             RedirectAttributes redirect) {
        guard(request, response);
        accounts.deactivateOfficer(id);
        redirect.addFlashAttribute("message", "Admissions officer deactivated.");
        return "redirect:/super-admin/admissions-officers";
    }

    private void guard(HttpServletRequest request, HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-store");
        accounts.requireSuperAdminForUpdate(request);
    }

    private void remember(HttpServletRequest request, Model model, String view) {
        request.setAttribute("officerErrorView", view);
        request.setAttribute("officerErrorModel", new LinkedHashMap<>(model.asMap()));
    }

    // Render after the transactional invocation has rolled back on a service validation failure.
    @ExceptionHandler(ResponseStatusException.class)
    public ModelAndView invalid(ResponseStatusException exception, HttpServletRequest request,
                                HttpServletResponse response) {
        if ((exception.getStatusCode().value() != 400 && exception.getStatusCode().value() != 409)
                || !(request.getAttribute("officerErrorView") instanceof String view)) throw exception;
        response.setStatus(exception.getStatusCode().value());
        ModelAndView page = new ModelAndView(view);
        if (request.getAttribute("officerErrorModel") instanceof Map<?, ?> attributes) {
            attributes.forEach((name, value) -> page.addObject(name.toString(), value));
        }
        page.addObject("error", exception.getReason());
        return page;
    }
}
