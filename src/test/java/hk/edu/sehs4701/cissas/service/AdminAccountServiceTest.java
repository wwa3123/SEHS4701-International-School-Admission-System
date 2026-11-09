package hk.edu.sehs4701.cissas.service;

import hk.edu.sehs4701.cissas.entity.Admin;
import hk.edu.sehs4701.cissas.enums.ActorRole;
import hk.edu.sehs4701.cissas.repository.AdminRepository;
import hk.edu.sehs4701.cissas.repository.IdentityReviewLockRepository;
import hk.edu.sehs4701.cissas.security.HashService;
import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AdminAccountServiceTest {
    private final AdminRepository admins = mock(AdminRepository.class);
    private final IdentityReviewLockRepository lock = mock(IdentityReviewLockRepository.class);
    private final HashService hash = mock(HashService.class);
    private final AdminAccountService accounts = new AdminAccountService(
            admins, lock, hash, Validation.buildDefaultValidatorFactory().getValidator());
    private final AdminAccountService.AccountRequest request =
            new AdminAccountService.AccountRequest("officer@example.com", "First", "Last", "password123");

    @Test
    void provisionsOnlyWhenNoSuperAdminExists() {
        when(hash.hashPassword("password123")).thenReturn("hash");
        when(admins.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        accounts.provisionSuperAdmin(request);
        verify(admins).save(argThat(admin -> admin.getRole() == ActorRole.ROLE_SUPER_ADMIN
                && "hash".equals(admin.getPasswordHash())));

        when(admins.existsByRole(ActorRole.ROLE_SUPER_ADMIN)).thenReturn(true);
        accounts.provisionSuperAdmin(new AdminAccountService.AccountRequest("", "", "", ""));
        verify(admins, times(1)).save(any());
        verify(lock, times(2)).acquireAccountLock();
        verify(lock, never()).acquire();
    }

    @Test
    void rejectsInvalidBootstrapDetails() {
        assertEquals(HttpStatus.BAD_REQUEST, assertThrows(ResponseStatusException.class,
                () -> accounts.provisionSuperAdmin(new AdminAccountService.AccountRequest("", "", "", "")))
                .getStatusCode());
        verify(admins, never()).save(any());
    }

    @Test
    void createsOfficerWithHashedPasswordAndAdminRole() {
        when(hash.hashPassword("password123")).thenReturn("hash");
        when(admins.save(any())).thenAnswer(invocation -> {
            Admin admin = invocation.getArgument(0);
            admin.setId(42L);
            return admin;
        });
        assertEquals("42", accounts.createOfficer(request).id());
        verify(admins).save(argThat(admin -> admin.getRole() == ActorRole.ROLE_ADMIN
                && "hash".equals(admin.getPasswordHash()) && admin.isActive() && admin.isPasswordChangeRequired()));
        verify(lock).acquireAccountLock();
        verify(lock, never()).acquire();
    }

    @Test
    void rejectsDuplicateEmailAndLongPasswords() {
        when(admins.findByEmailIgnoreCase("officer@example.com")).thenReturn(Optional.of(new Admin()));
        assertEquals(HttpStatus.CONFLICT, assertThrows(ResponseStatusException.class,
                () -> accounts.createOfficer(request)).getStatusCode());
        assertEquals(HttpStatus.BAD_REQUEST, assertThrows(ResponseStatusException.class,
                () -> accounts.createOfficer(new AdminAccountService.AccountRequest(
                        "another@example.com", "First", "Last", "a".repeat(73)))).getStatusCode());
        verify(admins, never()).save(any());
    }

    @Test
    void deactivatesOfficersButNotSuperAdmins() {
        Admin admin = new Admin();
        admin.setId(42L);
        when(admins.findLockedById(42L)).thenReturn(Optional.of(admin));
        when(admins.save(admin)).thenReturn(admin);
        assertFalse(accounts.deactivateOfficer(42L).active());
        admin.setRole(ActorRole.ROLE_SUPER_ADMIN);
        assertEquals(HttpStatus.NOT_FOUND, assertThrows(ResponseStatusException.class,
                () -> accounts.deactivateOfficer(42L)).getStatusCode());
    }

    @Test
    void changesOnlyActiveAdminsPasswordAfterVerifyingCurrentPassword() {
        Admin admin = new Admin();
        admin.setId(42L);
        admin.setPasswordHash("old-hash");
        when(admins.findLockedById(42L)).thenReturn(Optional.of(admin));
        when(hash.verifyPassword("old-password", "old-hash")).thenReturn(true);
        when(hash.hashPassword("new-password")).thenReturn("new-hash");
        MockHttpServletRequest servletRequest = new MockHttpServletRequest();
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("authenticatedUserId", 42L);
        session.setAttribute("authenticatedUserRole", "ROLE_ADMIN");
        session.setAttribute("adminCredentialVersion", 0L);
        servletRequest.setSession(session);

        assertEquals(HttpStatus.UNAUTHORIZED, assertThrows(ResponseStatusException.class,
                () -> accounts.changePassword(servletRequest,
                        new AdminAccountService.PasswordChangeRequest("wrong", "new-password"))).getStatusCode());
        assertEquals(HttpStatus.BAD_REQUEST, assertThrows(ResponseStatusException.class,
                () -> accounts.changePassword(servletRequest,
                        new AdminAccountService.PasswordChangeRequest("old-password", "é".repeat(37)))).getStatusCode());
        assertEquals(HttpStatus.BAD_REQUEST, assertThrows(ResponseStatusException.class,
                () -> accounts.changePassword(servletRequest,
                        new AdminAccountService.PasswordChangeRequest("old-password", "old-password"))).getStatusCode());
        verify(admins, never()).save(any());

        accounts.changePassword(servletRequest,
                new AdminAccountService.PasswordChangeRequest("old-password", "new-password"));
        assertEquals("new-hash", admin.getPasswordHash());
        assertEquals(1, admin.getCredentialVersion());
        verify(admins).save(admin);

        admin.setActive(false);
        assertEquals(HttpStatus.FORBIDDEN, assertThrows(ResponseStatusException.class,
                () -> accounts.changePassword(servletRequest,
                        new AdminAccountService.PasswordChangeRequest("old-password", "new-password"))).getStatusCode());
        session.setAttribute("authenticatedUserRole", "ROLE_MEMBER");
        admin.setActive(true);
        assertEquals(HttpStatus.FORBIDDEN, assertThrows(ResponseStatusException.class,
                () -> accounts.changePassword(servletRequest,
                        new AdminAccountService.PasswordChangeRequest("old-password", "new-password"))).getStatusCode());
    }

    @Test
    void resetRevokesExistingSessionsAndRequiresPasswordChange() {
        Admin admin = new Admin();
        admin.setId(42L);
        when(admins.findById(42L)).thenReturn(Optional.of(admin));
        when(admins.findLockedById(42L)).thenReturn(Optional.of(admin));
        when(hash.hashPassword("temporary-password")).thenReturn("new-hash");
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpSession oldSession = new MockHttpSession();
        oldSession.setAttribute("authenticatedUserId", 42L);
        oldSession.setAttribute("authenticatedUserRole", "ROLE_ADMIN");
        oldSession.setAttribute("adminCredentialVersion", 0L);
        request.setSession(oldSession);
        assertEquals(42L, accounts.requireAdmin(request));
        accounts.resetOfficerPassword(42L, new AdminAccountService.PasswordResetRequest("temporary-password"));
        assertEquals(1, admin.getCredentialVersion());
        assertTrue(admin.isPasswordChangeRequired());
        assertEquals(HttpStatus.FORBIDDEN, assertThrows(ResponseStatusException.class,
                () -> accounts.requireAdmin(request)).getStatusCode());
        oldSession.setAttribute("adminCredentialVersion", 1L);
        assertEquals(new AdminAccountService.PasswordSession(42L, 1), accounts.requirePasswordSession(request));
        assertEquals(HttpStatus.FORBIDDEN, assertThrows(ResponseStatusException.class,
                () -> accounts.requireAdmin(request)).getStatusCode());
        when(hash.verifyPassword("temporary-password", "new-hash")).thenReturn(true);
        accounts.changePassword(request, new AdminAccountService.PasswordChangeRequest(
                "temporary-password", "private-password"));
        assertFalse(admin.isPasswordChangeRequired());
        assertEquals(2, admin.getCredentialVersion());
        assertEquals(HttpStatus.FORBIDDEN, assertThrows(ResponseStatusException.class,
                () -> accounts.requireAdmin(request)).getStatusCode());
        oldSession.setAttribute("adminCredentialVersion", 2L);
        assertEquals(42L, accounts.requireAdmin(request));
    }

    @Test
    void recoveryOnlyResetsAnActiveSuperAdmin() {
        Admin admin = new Admin();
        admin.setId(42L);
        admin.setRole(ActorRole.ROLE_SUPER_ADMIN);
        when(admins.findLockedByEmailIgnoreCase("super@example.com")).thenReturn(Optional.of(admin));
        when(hash.hashPassword("temporary-password")).thenReturn("new-hash");
        accounts.recoverSuperAdmin("super@example.com", "temporary-password");
        verify(admins, never()).findByEmailIgnoreCase(any());
        assertEquals(1, admin.getCredentialVersion());
        assertTrue(admin.isPasswordChangeRequired());
        admin.setRole(ActorRole.ROLE_ADMIN);
        assertEquals(HttpStatus.NOT_FOUND, assertThrows(ResponseStatusException.class,
                () -> accounts.recoverSuperAdmin("super@example.com", "temporary-password")).getStatusCode());
    }
}
