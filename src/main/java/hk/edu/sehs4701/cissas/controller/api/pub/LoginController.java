package hk.edu.sehs4701.cissas.controller.api.pub;

import hk.edu.sehs4701.cissas.dto.internal.LoginResult;
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
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.http.HttpStatus;

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

        LoginResult result = loginService.login(request.email(), request.password(), request.accountType());
        LoginResponse response = result.response();
        HttpSession session = servletRequest.getSession(true);
        servletRequest.changeSessionId();
        session.setAttribute("authenticatedUserId", response.id());
        session.setAttribute("authenticatedUserRole", response.role().name());
        session.setAttribute("adminCredentialVersion", result.credentialVersion());
        return response;
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
    }
}
