package hk.edu.sehs4701.cissas.controller.api.pub;

import hk.edu.sehs4701.cissas.dto.request.LoginRequest;
import hk.edu.sehs4701.cissas.dto.response.LoginResponse;
import hk.edu.sehs4701.cissas.service.LoginService;
import hk.edu.sehs4701.cissas.service.RateLimitService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/public/auth")
public class LoginController {
    private static final String LOGIN_RATE_LIMIT_MESSAGE = "Too many login attempts";

    private final LoginService loginService;
    private final RateLimitService rateLimitService;

    @Autowired
    public LoginController(LoginService loginService, RateLimitService rateLimitService) {
        this.loginService = loginService;
        this.rateLimitService = rateLimitService;
    }

    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request, HttpServletRequest servletRequest) {
        String clientAddress = rateLimitService.resolveClientAddress(servletRequest);
        rateLimitService.checkLoginAllowed("login-ip:" + clientAddress, LOGIN_RATE_LIMIT_MESSAGE);

        LoginResponse response = loginService.login(request.email(), request.password(), request.accountType());
        HttpSession session = servletRequest.getSession(true);
        servletRequest.changeSessionId();
        session.setAttribute("authenticatedUserId", response.id());
        session.setAttribute("authenticatedUserRole", response.role().name());
        return response;
    }
}
