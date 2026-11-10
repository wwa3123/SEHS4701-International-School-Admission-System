package hk.edu.sehs4701.cissas.service;

import hk.edu.sehs4701.cissas.dto.internal.PasswordSession;
import hk.edu.sehs4701.cissas.dto.request.AccountRequest;
import hk.edu.sehs4701.cissas.dto.request.PasswordChangeRequest;
import hk.edu.sehs4701.cissas.dto.request.PasswordResetRequest;
import hk.edu.sehs4701.cissas.dto.response.AccountResponse;
import hk.edu.sehs4701.cissas.entity.Admin;
import hk.edu.sehs4701.cissas.enums.ActorRole;
import hk.edu.sehs4701.cissas.repository.AdminRepository;
import hk.edu.sehs4701.cissas.repository.IdentityReviewLockRepository;
import hk.edu.sehs4701.cissas.security.HashService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Validator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

@Service
public class AdminAccountService {
    private final AdminRepository admins;
    private final IdentityReviewLockRepository lock;
    private final HashService hashService;
    private final Validator validator;

    @Autowired
    public AdminAccountService(AdminRepository admins, IdentityReviewLockRepository lock,
                               HashService hashService, Validator validator) {
        this.admins = admins;
        this.lock = lock;
        this.hashService = hashService;
        this.validator = validator;
    }

    @Transactional(readOnly = true)
    public Long requireAdmin(HttpServletRequest request) {
        Admin admin = authenticatedAdmin(request);
        if (admin.isPasswordChangeRequired()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Password change required");
        }
        return admin.getId();
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public Long requireAdminForUpdate(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        Admin admin = admins.findLockedById(sessionId(session))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "Admin access required"));
        validateSession(admin, session);
        if (admin.isPasswordChangeRequired()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Password change required");
        }
        return admin.getId();
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void requireSuperAdminForUpdate(HttpServletRequest request) {
        requireAdminForUpdate(request);
        if (!ActorRole.ROLE_SUPER_ADMIN.name().equals(request.getSession(false).getAttribute("authenticatedUserRole"))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Super admin access required");
        }
    }

    @Transactional(readOnly = true)
    public PasswordSession requirePasswordSession(HttpServletRequest request) {
        Admin admin = authenticatedAdmin(request);
        return new PasswordSession(admin.getId(), admin.getCredentialVersion());
    }

    private Admin authenticatedAdmin(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        Long id = sessionId(session);
        Admin admin = admins.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "Admin access required"));
        validateSession(admin, session);
        return admin;
    }

    @Transactional
    public void provisionSuperAdmin(AccountRequest request) {
        // Serialize provisioning and officer creation across application instances.
        lock.acquireAccountLock();
        if (admins.existsByRole(ActorRole.ROLE_SUPER_ADMIN)) {
            return;
        }
        createAccount(request, ActorRole.ROLE_SUPER_ADMIN);
    }

    @Transactional
    public AccountResponse createOfficer(AccountRequest request) {
        lock.acquireAccountLock();
        return response(createAccount(request, ActorRole.ROLE_ADMIN));
    }

    @Transactional(readOnly = true)
    public List<AccountResponse> listOfficers() {
        return admins.findAllByRoleOrderByEmailAsc(ActorRole.ROLE_ADMIN).stream()
                .map(this::response)
                .toList();
    }

    @Transactional
    public AccountResponse deactivateOfficer(Long id) {
        Admin admin = admins.findLockedById(id)
                .filter(account -> account.getRole() == ActorRole.ROLE_ADMIN)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Admissions officer not found"));
        admin.setActive(false);
        admin.setCredentialVersion(admin.getCredentialVersion() + 1);
        return response(admins.save(admin));
    }

    @Transactional
    public void resetOfficerPassword(Long id, PasswordResetRequest request) {
        Admin admin = admins.findLockedById(id)
                .filter(account -> account.getRole() == ActorRole.ROLE_ADMIN && account.isActive())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Active admissions officer not found"));
        resetPassword(admin, request == null ? null : request.newPassword(), true);
    }

    @Transactional
    public void recoverSuperAdmin(String email, String password) {
        if (email == null || email.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Super admin email required");
        }
        Admin admin = admins.findLockedByEmailIgnoreCase(email.trim())
                .filter(value -> value.getRole() == ActorRole.ROLE_SUPER_ADMIN && value.isActive())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Active super admin not found"));
        resetPassword(admin, password, true);
    }

    @Transactional
    public void changePassword(HttpServletRequest request, PasswordChangeRequest change) {
        HttpSession session = request.getSession(false);
        Long id = sessionId(session);
        Admin admin = admins.findLockedById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "Admin access required"));
        validateSession(admin, session);
        if (change == null || change.currentPassword() == null || change.currentPassword().isBlank()
                || change.currentPassword().getBytes(StandardCharsets.UTF_8).length > 72
                || validPassword(change.newPassword())
                || change.currentPassword().equals(change.newPassword())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid password");
        }
        if (!hashService.verifyPassword(change.currentPassword(), admin.getPasswordHash())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Incorrect current password");
        }
        resetPassword(admin, change.newPassword(), false);
    }

    private Long sessionId(HttpSession session) {
        if (session == null || !(session.getAttribute("authenticatedUserId") instanceof Long id)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Admin login required");
        }
        return id;
    }

    private void validateSession(Admin admin, HttpSession session) {
        if (!admin.isActive()
                || (admin.getRole() != ActorRole.ROLE_ADMIN && admin.getRole() != ActorRole.ROLE_SUPER_ADMIN)
                || !admin.getRole().name().equals(session.getAttribute("authenticatedUserRole"))
                || !Objects.equals(admin.getCredentialVersion(), session.getAttribute("adminCredentialVersion"))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Admin access required");
        }
    }

    private void resetPassword(Admin admin, String password, boolean changeRequired) {
        if (validPassword(password)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid password");
        }
        admin.setPasswordHash(hashService.hashPassword(password));
        admin.setCredentialVersion(admin.getCredentialVersion() + 1);
        admin.setPasswordChangeRequired(changeRequired);
        admins.save(admin);
    }

    private boolean validPassword(String password) {
        return password == null || password.isBlank() || password.length() < 8
                || password.getBytes(StandardCharsets.UTF_8).length > 72;
    }

    private Admin createAccount(AccountRequest request, ActorRole role) {
        if (request == null || !validator.validate(request).isEmpty()
                || validPassword(request.password())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid account details or password");
        }
        String email = request.email().trim().toLowerCase(Locale.ROOT);
        if (admins.findByEmailIgnoreCase(email).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Admin email already exists");
        }
        Admin admin = new Admin();
        admin.setEmail(email);
        admin.setFirstName(request.firstName().trim());
        admin.setLastName(request.lastName().trim());
        admin.setPasswordHash(hashService.hashPassword(request.password()));
        admin.setRole(role);
        admin.setPasswordChangeRequired(role == ActorRole.ROLE_ADMIN);
        return admins.save(admin);
    }

    private AccountResponse response(Admin admin) {
        return new AccountResponse(admin.getId().toString(), admin.getEmail(),
                admin.getFirstName(), admin.getLastName(), admin.isActive());
    }
}
