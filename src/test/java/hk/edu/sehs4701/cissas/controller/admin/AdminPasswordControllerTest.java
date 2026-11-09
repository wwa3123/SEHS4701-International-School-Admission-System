package hk.edu.sehs4701.cissas.controller.admin;

import hk.edu.sehs4701.cissas.controller.api.admin.AdminPasswordController;
import hk.edu.sehs4701.cissas.service.AdminAccountService;
import hk.edu.sehs4701.cissas.service.RateLimitService;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AdminPasswordControllerTest {
    @Test
    void signsOutOnlyAfterSuccessfulPasswordChange() throws Exception {
        AdminAccountService accounts = mock(AdminAccountService.class);
        RateLimitService rateLimits = mock(RateLimitService.class);
        when(accounts.requirePasswordSession(any())).thenReturn(new AdminAccountService.PasswordSession(1L, 0));
        var mvc = MockMvcBuilders.standaloneSetup(new AdminPasswordController(accounts, rateLimits)).build();
        String body = """
                {"currentPassword":"old-password","newPassword":"new-password"}
                """;
        MockHttpSession failed = new MockHttpSession();
        failed.setAttribute("authenticatedUserId", 1L);
        failed.setAttribute("authenticatedUserRole", "ROLE_ADMIN");
        doThrow(new ResponseStatusException(HttpStatus.UNAUTHORIZED)).when(accounts).changePassword(any(), any());
        mvc.perform(put("/api/admin/password").session(failed).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnauthorized());
        org.junit.jupiter.api.Assertions.assertFalse(failed.isInvalid());

        reset(accounts);
        when(accounts.requirePasswordSession(any())).thenReturn(new AdminAccountService.PasswordSession(1L, 0));
        MockHttpSession success = new MockHttpSession();
        success.setAttribute("authenticatedUserId", 1L);
        success.setAttribute("authenticatedUserRole", "ROLE_ADMIN");
        mvc.perform(put("/api/admin/password").session(success).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isNoContent());
        assertTrue(success.isInvalid());
        verify(rateLimits, times(2)).checkAllowed("admin-password-account:1:0", "Too many password attempts");
        verifyNoMoreInteractions(rateLimits);
    }

    @Test
    void rateLimitStopsPasswordVerification() throws Exception {
        AdminAccountService accounts = mock(AdminAccountService.class);
        RateLimitService rateLimits = mock(RateLimitService.class);
        when(accounts.requirePasswordSession(any())).thenReturn(new AdminAccountService.PasswordSession(1L, 0));
        doThrow(new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS))
                .when(rateLimits).checkAllowed("admin-password-account:1:0", "Too many password attempts");
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("authenticatedUserId", 1L);
        session.setAttribute("authenticatedUserRole", "ROLE_ADMIN");
        MockMvcBuilders.standaloneSetup(new AdminPasswordController(accounts, rateLimits)).build()
                .perform(put("/api/admin/password").session(session).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"currentPassword":"old-password","newPassword":"new-password"}
                                """))
                .andExpect(status().isTooManyRequests());
        verify(accounts, never()).changePassword(any(), any());
    }

    @Test
    void revokedSessionDoesNotConsumeAccountLimit() throws Exception {
        AdminAccountService accounts = mock(AdminAccountService.class);
        RateLimitService rateLimits = mock(RateLimitService.class);
        when(accounts.requirePasswordSession(any())).thenThrow(new ResponseStatusException(HttpStatus.FORBIDDEN));
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("authenticatedUserId", 1L);
        session.setAttribute("authenticatedUserRole", "ROLE_ADMIN");
        MockMvcBuilders.standaloneSetup(new AdminPasswordController(accounts, rateLimits)).build()
                .perform(put("/api/admin/password").session(session).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"currentPassword":"old-password","newPassword":"new-password"}
                                """))
                .andExpect(status().isForbidden());
        verify(rateLimits, never()).checkAllowed(startsWith("admin-password-account:"), anyString());
        verifyNoInteractions(rateLimits);
        verify(accounts, never()).changePassword(any(), any());
    }

    @Test
    void accountLimitUsesValidatedCredentialVersion() throws Exception {
        AdminAccountService accounts = mock(AdminAccountService.class);
        RateLimitService rateLimits = mock(RateLimitService.class);
        when(accounts.requirePasswordSession(any())).thenReturn(new AdminAccountService.PasswordSession(1L, 3));
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("authenticatedUserId", 1L);
        session.setAttribute("authenticatedUserRole", "ROLE_ADMIN");
        MockMvcBuilders.standaloneSetup(new AdminPasswordController(accounts, rateLimits)).build()
                .perform(put("/api/admin/password").session(session).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"currentPassword":"old-password","newPassword":"new-password"}
                                """))
                .andExpect(status().isNoContent());
        verify(rateLimits).checkAllowed("admin-password-account:1:3", "Too many password attempts");
        verifyNoMoreInteractions(rateLimits);
    }

    @Test
    void passwordAttemptsUseSeparateAccountLimitsOnSharedAddress() throws Exception {
        AdminAccountService accounts = mock(AdminAccountService.class);
        RateLimitService rateLimits = mock(RateLimitService.class);
        when(accounts.requirePasswordSession(any())).thenReturn(
                new AdminAccountService.PasswordSession(1L, 0),
                new AdminAccountService.PasswordSession(2L, 0));
        doThrow(new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS))
                .when(rateLimits).checkAllowed("admin-password-account:1:0", "Too many password attempts");
        var mvc = MockMvcBuilders.standaloneSetup(new AdminPasswordController(accounts, rateLimits)).build();
        String body = """
                {"currentPassword":"old-password","newPassword":"new-password"}
                """;
        mvc.perform(put("/api/admin/password").session(new MockHttpSession())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isTooManyRequests());
        mvc.perform(put("/api/admin/password").session(new MockHttpSession())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isNoContent());
        verify(rateLimits).checkAllowed("admin-password-account:1:0", "Too many password attempts");
        verify(rateLimits).checkAllowed("admin-password-account:2:0", "Too many password attempts");
        verifyNoMoreInteractions(rateLimits);
    }
}
