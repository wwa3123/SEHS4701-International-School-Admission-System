package hk.edu.sehs4701.cissas.service;

import hk.edu.sehs4701.cissas.entity.Member;
import hk.edu.sehs4701.cissas.enums.AccountStatus;
import hk.edu.sehs4701.cissas.repository.MemberRepository;
import hk.edu.sehs4701.cissas.security.HashService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MemberPasswordResetServiceTest {
    private final MemberRepository members = mock(MemberRepository.class);
    private final OtcService otcs = mock(OtcService.class);
    private final HashService hashes = mock(HashService.class);
    private final MemberPasswordResetService service = new MemberPasswordResetService(members, otcs, hashes);

    @Test
    void resetRequestDoesNotRevealUnknownOrInactiveMembers() {
        service.requestCode("unknown@example.com");
        Member inactive = member();
        inactive.setAccountStatus(AccountStatus.SUSPENDED);
        when(members.findByEmailIgnoreCase("inactive@example.com")).thenReturn(Optional.of(inactive));

        service.requestCode("inactive@example.com");

        verifyNoInteractions(otcs);
    }

    @Test
    void resetRequestSendsCodeOnlyForActiveMemberAtRegisteredAddress() {
        Member member = member();
        when(members.findByEmailIgnoreCase("member@example.com")).thenReturn(Optional.of(member));

        service.requestCode(" MEMBER@example.com ");

        verify(otcs).issuePasswordResetCode(member);
    }

    @Test
    void resetUsesOneTimeCodeChangesPasswordAndInvalidatesSessions() {
        Member member = member();
        member.setCredentialVersion(3);
        member.setFailedLoginAttempts(5);
        member.setLoginLockedUntil(java.time.LocalDateTime.now().plusMinutes(15));
        when(members.findByEmailIgnoreCase("member@example.com")).thenReturn(Optional.of(member));
        when(hashes.hashPassword("new-pass1")).thenReturn("new-hash");

        service.resetPassword(" MEMBER@example.com ", "123456", "new-pass1");

        verify(otcs).verifyPasswordResetCode(7L, "123456");
        assertEquals("new-hash", member.getPasswordHash());
        assertEquals(4, member.getCredentialVersion());
        assertEquals(0, member.getFailedLoginAttempts());
        assertNull(member.getLoginLockedUntil());
    }

    @Test
    void resetRejectsPasswordThatMatchesEmailBeforeConsumingCode() {
        Member member = member();
        member.setEmail("member123@example.com");
        when(members.findByEmailIgnoreCase("member123@example.com")).thenReturn(Optional.of(member));

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> service.resetPassword("member123@example.com", "123456", "MEMBER123@example.com"));

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
        verifyNoInteractions(otcs, hashes);
    }

    @Test
    void resetValidatesPasswordBeforeLookingUpAccount() {
        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> service.resetPassword("unknown@example.com", "123456", "weak"));

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
        verifyNoInteractions(members, otcs, hashes);
    }

    @Test
    void resetRejectsPasswordMatchingTrimmedEmailBeforeAccountLookup() {
        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> service.resetPassword(" MEMBER123@example.com ", "123456", "member123@example.com"));

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
        verifyNoInteractions(members, otcs, hashes);
    }

    private Member member() {
        Member member = new Member();
        member.setId(7L);
        member.setEmail("member@example.com");
        member.setAccountStatus(AccountStatus.ACTIVE);
        member.setPasswordHash("old-hash");
        return member;
    }
}
