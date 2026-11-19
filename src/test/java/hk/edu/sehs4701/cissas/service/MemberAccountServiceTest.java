package hk.edu.sehs4701.cissas.service;

import hk.edu.sehs4701.cissas.entity.Member;
import hk.edu.sehs4701.cissas.enums.AccountStatus;
import hk.edu.sehs4701.cissas.repository.MemberRepository;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

class MemberAccountServiceTest {
    private final MemberRepository members = mock(MemberRepository.class);
    private final MemberAccountService service = new MemberAccountService(members);

    @Test
    void deactivationSuspendsAndRevokesSessionsWithoutDeletingMember() {
        Member member = new Member();
        member.setAccountStatus(AccountStatus.ACTIVE);
        member.setCredentialVersion(4);
        when(members.findByIdForUpdate(12L)).thenReturn(Optional.of(member));

        service.deactivate(12L);

        assertEquals(AccountStatus.SUSPENDED, member.getAccountStatus());
        assertEquals(5, member.getCredentialVersion());
        verify(members).save(member);
        verify(members, never()).delete(any(Member.class));
    }

    @Test
    void repeatedDeactivationDoesNotChangeCredentialVersionAgain() {
        Member member = new Member();
        member.setAccountStatus(AccountStatus.SUSPENDED);
        member.setCredentialVersion(5);
        when(members.findByIdForUpdate(12L)).thenReturn(Optional.of(member));

        service.deactivate(12L);

        assertEquals(5, member.getCredentialVersion());
        verify(members, never()).save(any());
        verify(members, never()).delete(any(Member.class));
    }

    @Test
    void missingMemberReturnsNotFound() {
        when(members.findByIdForUpdate(12L)).thenReturn(Optional.empty());
        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> service.deactivate(12L));
        assertEquals(HttpStatus.NOT_FOUND, exception.getStatusCode());
        verify(members, never()).save(any());
    }
}
