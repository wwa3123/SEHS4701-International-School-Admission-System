package hk.edu.sehs4701.cissas.controller.web;

import hk.edu.sehs4701.cissas.dto.request.PasswordChangeRequest;
import hk.edu.sehs4701.cissas.service.AdminAccountService;
import hk.edu.sehs4701.cissas.service.RateLimitService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@Controller
@RequestMapping("/admin/password")
public class AdminPasswordPageController {
    private final AdminAccountService accounts;
    private final RateLimitService rateLimits;

    public AdminPasswordPageController(AdminAccountService accounts, RateLimitService rateLimits) {
        this.accounts = accounts;
        this.rateLimits = rateLimits;
    }

    @GetMapping
    public String password(HttpServletRequest request) {
        accounts.requirePasswordSession(request);
        return "admin/password";
    }

    @PostMapping
    public String password(@ModelAttribute PasswordChangeRequest form, HttpServletRequest request,
                           HttpServletResponse response, Model model) {
        var authenticated = accounts.requirePasswordSession(request);
        try {
            rateLimits.checkAllowed("admin-password-account:" + authenticated.id() + ":" + authenticated.credentialVersion(),
                    "Too many password attempts");
            accounts.changePassword(request, form);
        } catch (ResponseStatusException ex) {
            if (ex.getStatusCode().value() != 400 && ex.getStatusCode().value() != 401
                    && ex.getStatusCode().value() != 429) throw ex;
            response.setStatus(ex.getStatusCode().value());
            model.addAttribute("error", ex.getReason());
            return "admin/password";
        }
        request.getSession(false).invalidate();
        return "redirect:/login?passwordChanged";
    }
}
