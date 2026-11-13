package hk.edu.sehs4701.cissas.controller.web;

import hk.edu.sehs4701.cissas.dto.request.LoginRequest;
import hk.edu.sehs4701.cissas.enums.ActorRole;
import hk.edu.sehs4701.cissas.enums.LoginAccountType;
import hk.edu.sehs4701.cissas.service.LoginSessionService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.server.ResponseStatusException;

/**
 * GET /login renders the login DTO; POST /login binds form fields and POST /logout ends the session.
 * Thymeleaf forms use th:action so Spring Security supplies CSRF tokens automatically.
 * These form routes replace the former JSON /api/public/auth/login and /logout endpoints.
 */
@Controller
public class LoginPageController {
    private final LoginSessionService sessions;

    public LoginPageController(LoginSessionService sessions) {
        this.sessions = sessions;
    }

    @GetMapping("/login")
    public String login(Model model) {
        model.addAttribute("login", new LoginRequest("", "", LoginAccountType.MEMBER));
        return "auth/login";
    }

    @PostMapping("/login")
    public String login(@Valid @ModelAttribute("login") LoginRequest form, BindingResult errors,
                        HttpServletRequest request, HttpServletResponse response, Model model) {
        if (errors.hasErrors()) {
            response.setStatus(400);
            return "auth/login";
        }
        try {
            var user = sessions.login(form, request);
            if (user.role() == ActorRole.ROLE_MEMBER) return "redirect:/member/applications";
            return user.passwordChangeRequired() ? "redirect:/admin/password" : "redirect:/admin/applications";
        } catch (ResponseStatusException ex) {
            if (ex.getStatusCode().value() != 401 && ex.getStatusCode().value() != 429) throw ex;
            response.setStatus(ex.getStatusCode().value());
            model.addAttribute("error", ex.getReason());
            return "auth/login";
        }
    }

    @PostMapping("/logout")
    public String logout(HttpServletRequest request) {
        sessions.logout(request);
        return "redirect:/login?logout";
    }
}
