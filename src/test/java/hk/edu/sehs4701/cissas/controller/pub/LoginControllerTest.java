package hk.edu.sehs4701.cissas.controller.pub;

import hk.edu.sehs4701.cissas.controller.api.pub.LoginController;
import hk.edu.sehs4701.cissas.dto.internal.LoginResult;
import hk.edu.sehs4701.cissas.dto.request.LoginRequest;
import hk.edu.sehs4701.cissas.dto.response.LoginResponse;
import hk.edu.sehs4701.cissas.enums.ActorRole;
import hk.edu.sehs4701.cissas.enums.LoginAccountType;
import hk.edu.sehs4701.cissas.service.LoginService;
import hk.edu.sehs4701.cissas.service.RateLimitService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class LoginControllerTest {
    @Test
    void successfulLoginRotatesSessionAndStoresAuthenticatedIdentity() {
        LoginService loginService = mock(LoginService.class);
        RateLimitService rateLimitService = mock(RateLimitService.class);
        HttpServletRequest servletRequest = mock(HttpServletRequest.class);
        HttpSession session = mock(HttpSession.class);
        LoginController controller = new LoginController(loginService, rateLimitService);
        LoginResponse response = new LoginResponse(1L, "member@example.com", "Test", "Member", ActorRole.ROLE_MEMBER, false);
        when(loginService.login("member@example.com", "valid-password", LoginAccountType.MEMBER))
                .thenReturn(new LoginResult(response, null));
        when(rateLimitService.resolveClientAddress(servletRequest)).thenReturn("127.0.0.1");
        when(servletRequest.getSession(true)).thenReturn(session);

        assertEquals(response, controller.login(
                new LoginRequest("member@example.com", "valid-password", LoginAccountType.MEMBER),
                servletRequest
        ));

        verify(rateLimitService, times(1)).checkLoginAllowed("login-ip:127.0.0.1", "Too many login attempts");
        verify(servletRequest).changeSessionId();
        verify(session).setAttribute("authenticatedUserId", 1L);
        verify(session).setAttribute("authenticatedUserRole", "ROLE_MEMBER");
        verify(session).setAttribute("adminCredentialVersion", null);
    }

    @Test
    void failedLoginDoesNotCreateOrRotateSession() {
        LoginService loginService = mock(LoginService.class);
        RateLimitService rateLimitService = mock(RateLimitService.class);
        HttpServletRequest servletRequest = mock(HttpServletRequest.class);
        LoginController controller = new LoginController(loginService, rateLimitService);
        when(rateLimitService.resolveClientAddress(servletRequest)).thenReturn("127.0.0.1");
        when(loginService.login("member@example.com", "wrong-password", LoginAccountType.MEMBER))
                .thenThrow(new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid email or password"));

        assertThrows(
                ResponseStatusException.class,
                () -> controller.login(
                        new LoginRequest("member@example.com", "wrong-password", LoginAccountType.MEMBER),
                        servletRequest
                )
        );

        verify(rateLimitService, times(1)).checkLoginAllowed("login-ip:127.0.0.1", "Too many login attempts");
        verifyNoInteractions(servletRequest);
    }

    @Test
    void adminSessionReceivesVersionFromVerifiedCredentials() {
        LoginService loginService = mock(LoginService.class);
        RateLimitService rateLimitService = mock(RateLimitService.class);
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpSession session = mock(HttpSession.class);
        LoginResponse response = new LoginResponse(2L, "admin@example.com", "Test", "Admin", ActorRole.ROLE_ADMIN, true);
        when(loginService.login("admin@example.com", "password", LoginAccountType.ADMIN))
                .thenReturn(new LoginResult(response, 4L));
        when(request.getSession(true)).thenReturn(session);
        new LoginController(loginService, rateLimitService).login(
                new LoginRequest("admin@example.com", "password", LoginAccountType.ADMIN), request);
        verify(session).setAttribute("adminCredentialVersion", 4L);
    }

    @Test
    void loginResponseIdentifiesRequiredPasswordChange() throws Exception {
        LoginService loginService = mock(LoginService.class);
        RateLimitService rateLimitService = mock(RateLimitService.class);
        LoginResponse response = new LoginResponse(2L, "admin@example.com", "Test", "Admin", ActorRole.ROLE_ADMIN, true);
        when(loginService.login("admin@example.com", "password", LoginAccountType.ADMIN))
                .thenReturn(new LoginResult(response, 4L));
        MockMvcBuilders.standaloneSetup(new LoginController(loginService, rateLimitService)).build()
                .perform(post("/api/public/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"admin@example.com","password":"password","accountType":"ADMIN"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.passwordChangeRequired").value(true));
    }

    @Test
    void rateLimitedLoginDoesNotCheckCredentialsOrCreateSession() {
        LoginService loginService = mock(LoginService.class);
        RateLimitService rateLimitService = mock(RateLimitService.class);
        HttpServletRequest servletRequest = mock(HttpServletRequest.class);
        LoginController controller = new LoginController(loginService, rateLimitService);
        when(rateLimitService.resolveClientAddress(servletRequest)).thenReturn("127.0.0.1");
        doThrow(new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Too many login attempts"))
                .when(rateLimitService).checkLoginAllowed("login-ip:127.0.0.1", "Too many login attempts");

        assertThrows(
                ResponseStatusException.class,
                () -> controller.login(
                        new LoginRequest("member@example.com", "password", LoginAccountType.MEMBER),
                        servletRequest
                )
        );

        verifyNoInteractions(loginService, servletRequest);
    }
}
