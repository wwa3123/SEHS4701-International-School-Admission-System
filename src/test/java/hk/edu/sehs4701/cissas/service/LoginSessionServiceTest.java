package hk.edu.sehs4701.cissas.service;

import hk.edu.sehs4701.cissas.dto.internal.LoginResult;
import hk.edu.sehs4701.cissas.dto.request.LoginRequest;
import hk.edu.sehs4701.cissas.dto.response.LoginResponse;
import hk.edu.sehs4701.cissas.enums.ActorRole;
import hk.edu.sehs4701.cissas.enums.LoginAccountType;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class LoginSessionServiceTest {
    private final LoginService login = mock(LoginService.class);
    private final RateLimitService limits = mock(RateLimitService.class);
    private final LoginSessionService sessions = new LoginSessionService(login, limits);

    @Test
    void adminLoginStoresCredentialVersionAndClearsPreviousWorkflows() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpSession session = new MockHttpSession();
        request.setSession(session);
        session.setAttribute("offerConfirmation", "old");
        session.setAttribute("registrationVerifiedEmail", "old@example.test");
        session.setAttribute("registrationVerifiedUntil", "old");
        String oldId = session.getId();
        when(login.login("admin@example.test", "password", LoginAccountType.ADMIN)).thenReturn(
                new LoginResult(new LoginResponse(2L, "admin@example.test", "Admin", "User",
                        ActorRole.ROLE_ADMIN, true), 4L));
        sessions.login(new LoginRequest("admin@example.test", "password", LoginAccountType.ADMIN), request);
        assertNotEquals(oldId, session.getId());
        assertEquals(2L, session.getAttribute("authenticatedUserId"));
        assertEquals("ROLE_ADMIN", session.getAttribute("authenticatedUserRole"));
        assertEquals(4L, session.getAttribute("adminCredentialVersion"));
        assertNull(session.getAttribute("offerConfirmation"));
        assertNull(session.getAttribute("registrationVerifiedEmail"));
        assertNull(session.getAttribute("registrationVerifiedUntil"));
    }

    @Test
    void rateLimitedLoginDoesNotCheckCredentialsOrCreateSession() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        when(limits.resolveClientAddress(request)).thenReturn("127.0.0.1");
        doThrow(new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS))
                .when(limits).checkLoginAllowed("login-ip:127.0.0.1", "Too many login attempts");
        assertThrows(ResponseStatusException.class, () -> sessions.login(
                new LoginRequest("member@example.test", "password", LoginAccountType.MEMBER), request));
        verifyNoInteractions(login);
        assertNull(request.getSession(false));
    }

    @Test
    void failedLoginDoesNotCreateSessionAndLogoutIsIdempotent() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        when(login.login(anyString(), anyString(), any())).thenThrow(new ResponseStatusException(HttpStatus.UNAUTHORIZED));
        assertThrows(ResponseStatusException.class, () -> sessions.login(
                new LoginRequest("member@example.test", "wrong", LoginAccountType.MEMBER), request));
        assertNull(request.getSession(false));
        sessions.logout(request);
        assertNull(request.getSession(false));
        MockHttpSession session = new MockHttpSession();
        request.setSession(session);
        sessions.logout(request);
        assertTrue(session.isInvalid());
    }
}
