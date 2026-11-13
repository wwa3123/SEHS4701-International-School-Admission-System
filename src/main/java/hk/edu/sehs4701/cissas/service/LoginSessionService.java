package hk.edu.sehs4701.cissas.service;

import hk.edu.sehs4701.cissas.dto.request.LoginRequest;
import hk.edu.sehs4701.cissas.dto.response.LoginResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.web.csrf.HttpSessionCsrfTokenRepository;
import org.springframework.stereotype.Service;

@Service
public class LoginSessionService {
    private final LoginService login;
    private final RateLimitService rateLimits;

    public LoginSessionService(LoginService login, RateLimitService rateLimits) {
        this.login = login;
        this.rateLimits = rateLimits;
    }

    public LoginResponse login(LoginRequest credentials, HttpServletRequest request) {
        rateLimits.checkLoginAllowed("login-ip:" + rateLimits.resolveClientAddress(request), "Too many login attempts");
        var result = login.login(credentials.email(), credentials.password(), credentials.accountType());
        LoginResponse response = result.response();
        HttpSession session = request.getSession(true);
        request.changeSessionId();
        session.removeAttribute("offerConfirmation");
        session.removeAttribute("registrationVerifiedEmail");
        session.removeAttribute("registrationVerifiedUntil");
        session.setAttribute("authenticatedUserId", response.id());
        session.setAttribute("authenticatedUserRole", response.role().name());
        session.setAttribute("adminCredentialVersion", result.credentialVersion());
        new HttpSessionCsrfTokenRepository().saveToken(null, request, null);
        return response;
    }

    public void logout(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null) session.invalidate();
    }
}
