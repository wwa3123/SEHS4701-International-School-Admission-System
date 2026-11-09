package hk.edu.sehs4701.cissas.service;

import hk.edu.sehs4701.cissas.dto.response.LoginResponse;
import hk.edu.sehs4701.cissas.entity.Admin;
import hk.edu.sehs4701.cissas.entity.Member;
import hk.edu.sehs4701.cissas.enums.AccountStatus;
import hk.edu.sehs4701.cissas.enums.ActorRole;
import hk.edu.sehs4701.cissas.enums.LoginAccountType;
import hk.edu.sehs4701.cissas.repository.AdminRepository;
import hk.edu.sehs4701.cissas.repository.MemberRepository;
import hk.edu.sehs4701.cissas.security.HashService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.util.Locale;

@Service
public class LoginService {
    public record LoginResult(LoginResponse response, Long credentialVersion) {}

    private final MemberRepository memberRepository;
    private final AdminRepository adminRepository;
    private final HashService hashService;

    public LoginService(MemberRepository memberRepository, AdminRepository adminRepository, HashService hashService) {
        this.memberRepository = memberRepository;
        this.adminRepository = adminRepository;
        this.hashService = hashService;
    }

    @Transactional(readOnly = true)
    public LoginResult login(String email, String password, LoginAccountType accountType) {
        if (email == null || email.isBlank()
                || password == null || password.isBlank()
                || accountType == null
                || password.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw invalidCredentials();
        }

        String normalizedEmail = email.trim().toLowerCase(Locale.ROOT);
        if (accountType == LoginAccountType.MEMBER) {
            Member member = memberRepository.findFirstByEmailIgnoreCase(normalizedEmail).orElse(null);
            boolean passwordMatches = hashService.verifyPassword(
                    password, member == null ? null : member.getPasswordHash()
            );
            if (member == null || member.getAccountStatus() != AccountStatus.ACTIVE || !passwordMatches) {
                throw invalidCredentials();
            }
            return new LoginResult(new LoginResponse(member.getId(), member.getEmail(), member.getFirstName(),
                    member.getLastName(), ActorRole.ROLE_MEMBER, false), null);
        }

        Admin admin = adminRepository.findByEmailIgnoreCase(normalizedEmail).orElse(null);
        boolean passwordMatches = hashService.verifyPassword(password, admin == null ? null : admin.getPasswordHash());
        if (admin == null || !admin.isActive() || !passwordMatches) {
            throw invalidCredentials();
        }
        return new LoginResult(new LoginResponse(admin.getId(), admin.getEmail(), admin.getFirstName(),
                admin.getLastName(), admin.getRole(), admin.isPasswordChangeRequired()), admin.getCredentialVersion());
    }

    private ResponseStatusException invalidCredentials() {
        return new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid email or password");
    }
}
