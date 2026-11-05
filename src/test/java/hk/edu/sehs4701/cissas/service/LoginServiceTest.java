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
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

class LoginServiceTest {
    private final MemberRepository memberRepository = mock(MemberRepository.class);
    private final AdminRepository adminRepository = mock(AdminRepository.class);
    private final HashService hashService = mock(HashService.class);
    private final LoginService service = new LoginService(memberRepository, adminRepository, hashService);

    @Test
    void logsInActiveMember() {
        Member member = new Member();
        member.setId(1L);
        member.setEmail("member@example.com");
        member.setFirstName("Test");
        member.setLastName("Member");
        member.setAccountStatus(AccountStatus.ACTIVE);
        member.setPasswordHash("member-hash");
        when(memberRepository.findFirstByEmailIgnoreCase("member@example.com")).thenReturn(Optional.of(member));
        when(hashService.verifyPassword("valid-password", "member-hash")).thenReturn(true);

        assertEquals(
                new LoginResponse(1L, "member@example.com", "Test", "Member", ActorRole.ROLE_MEMBER),
                service.login(" MEMBER@example.com ", "valid-password", LoginAccountType.MEMBER)
        );
        verifyNoInteractions(adminRepository);
    }

    @Test
    void logsInActiveAdmin() {
        Admin admin = new Admin();
        admin.setId(2L);
        admin.setEmail("admin@example.com");
        admin.setFirstName("Test");
        admin.setLastName("Admin");
        admin.setPasswordHash("admin-hash");
        Member memberWithSameEmail = new Member();
        memberWithSameEmail.setEmail("admin@example.com");
        memberWithSameEmail.setAccountStatus(AccountStatus.ACTIVE);
        memberWithSameEmail.setPasswordHash("admin-hash");
        when(memberRepository.findFirstByEmailIgnoreCase("admin@example.com"))
                .thenReturn(Optional.of(memberWithSameEmail));
        when(adminRepository.findByEmailIgnoreCase("admin@example.com")).thenReturn(Optional.of(admin));
        when(hashService.verifyPassword("valid-password", "admin-hash")).thenReturn(true);

        assertEquals(ActorRole.ROLE_ADMIN,
                service.login("admin@example.com", "valid-password", LoginAccountType.ADMIN).role());
        verifyNoInteractions(memberRepository);
    }

    @Test
    void accountTypeSelectsCorrectAccountWhenEmailExistsInBothTables() {
        Member member = new Member();
        member.setId(1L);
        member.setEmail("shared@example.com");
        member.setFirstName("Member");
        member.setLastName("Account");
        member.setAccountStatus(AccountStatus.ACTIVE);
        member.setPasswordHash("member-hash");
        when(memberRepository.findFirstByEmailIgnoreCase("shared@example.com")).thenReturn(Optional.of(member));
        when(hashService.verifyPassword("member-password", "member-hash")).thenReturn(true);

        LoginResponse response = service.login(
                "shared@example.com", "member-password", LoginAccountType.MEMBER
        );

        assertEquals(ActorRole.ROLE_MEMBER, response.role());
        verifyNoInteractions(adminRepository);
    }

    @Test
    void rejectsInactiveMemberWithGenericUnauthorizedResponse() {
        Member member = new Member();
        member.setPasswordHash("member-hash");
        member.setAccountStatus(AccountStatus.UNVERIFIED);
        when(memberRepository.findFirstByEmailIgnoreCase("member@example.com")).thenReturn(Optional.of(member));

        assertUnauthorized(() -> service.login("member@example.com", "valid-password", LoginAccountType.MEMBER));
        verify(hashService).verifyPassword("valid-password", "member-hash");
    }

    @Test
    void rejectsWrongPasswordWithGenericUnauthorizedResponse() {
        Member member = new Member();
        member.setPasswordHash("member-hash");
        member.setAccountStatus(AccountStatus.ACTIVE);
        when(memberRepository.findFirstByEmailIgnoreCase("member@example.com")).thenReturn(Optional.of(member));
        when(hashService.verifyPassword("wrong-password", "member-hash")).thenReturn(false);

        assertUnauthorized(() -> service.login("member@example.com", "wrong-password", LoginAccountType.MEMBER));
    }

    @Test
    void rejectsUnknownEmailAfterPerformingBothDummyPasswordChecks() {
        when(memberRepository.findFirstByEmailIgnoreCase("unknown@example.com")).thenReturn(Optional.empty());
        assertUnauthorized(() -> service.login(
                "unknown@example.com", "valid-password", LoginAccountType.MEMBER
        ));
        verify(hashService).verifyPassword("valid-password", null);
    }

    @Test
    void rejectsPasswordsLongerThanBcryptLimitBeforeLookup() {
        assertUnauthorized(() -> service.login(
                "member@example.com", "a".repeat(73), LoginAccountType.MEMBER
        ));
        verifyNoInteractions(memberRepository, adminRepository, hashService);
    }

    private void assertUnauthorized(Runnable login) {
        ResponseStatusException exception = assertThrows(ResponseStatusException.class, login::run);
        assertEquals(HttpStatus.UNAUTHORIZED, exception.getStatusCode());
        assertEquals("Invalid email or password", exception.getReason());
    }
}
