package hk.edu.sehs4701.cissas.service;

import hk.edu.sehs4701.cissas.dto.request.MembershipRegistrationRequest;
import hk.edu.sehs4701.cissas.entity.IdentityDocument;
import hk.edu.sehs4701.cissas.entity.Member;
import hk.edu.sehs4701.cissas.enums.AccountStatus;
import hk.edu.sehs4701.cissas.enums.IdentityDocumentType;
import hk.edu.sehs4701.cissas.repository.MemberRepository;
import hk.edu.sehs4701.cissas.security.HashService;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

class MembershipRegistrationServiceTest {
    @Test
    void pendingRegistrationCanResendButCannotReplaceAccountDetails() {
        MemberRepository memberRepository = mock(MemberRepository.class);
        IdentityDocumentService identityDocumentService = mock(IdentityDocumentService.class);
        HashService hashService = mock(HashService.class);
        OtcService otcService = mock(OtcService.class);
        MembershipRegistrationService service = new MembershipRegistrationService(
                memberRepository, identityDocumentService, hashService, otcService
        );

        Member member = new Member();
        member.setId(1L);
        member.setEmail("member@example.com");
        member.setPasswordHash("original-password-hash");
        member.setFirstName("Original");
        member.setLastName("Member");
        member.setPhoneNumber("12345678");
        member.setAccountStatus(AccountStatus.UNVERIFIED);
        when(memberRepository.findByEmailIgnoreCase("member@example.com")).thenReturn(Optional.of(member));

        service.register(new MembershipRegistrationRequest(
                "Attacker", "Name", "member@example.com", "87654321",
                null, null, null, "attacker-password"
        ));

        assertEquals("original-password-hash", member.getPasswordHash());
        assertEquals("Original", member.getFirstName());
        assertEquals("Member", member.getLastName());
        assertEquals("12345678", member.getPhoneNumber());
        verify(otcService).throttleAndResendOtc(member);
        verifyNoInteractions(identityDocumentService, hashService);
    }

    @Test
    void newRegistrationSavesMemberAndIssuesVerificationCode() {
        MemberRepository memberRepository = mock(MemberRepository.class);
        IdentityDocumentService identityDocumentService = mock(IdentityDocumentService.class);
        HashService hashService = mock(HashService.class);
        OtcService otcService = mock(OtcService.class);
        MembershipRegistrationService service = new MembershipRegistrationService(
                memberRepository, identityDocumentService, hashService, otcService
        );
        IdentityDocument document = new IdentityDocument();
        document.setDocumentNumberHash("document-hash");
        when(identityDocumentService.createIdentityDocument(
                IdentityDocumentType.HKID, "A123456(8)", null
        )).thenReturn(document);
        when(memberRepository.findByEmailIgnoreCase("member@example.com")).thenReturn(Optional.empty());
        when(memberRepository.save(any(Member.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.register(validRequest());

        verify(memberRepository).save(argThat(member ->
                member.getAccountStatus() == AccountStatus.UNVERIFIED
                        && member.getEmail().equals("member@example.com")
                        && member.getIdentityDocuments().size() == 1
                        && member.getIdentityDocuments().getFirst().getDocumentNumberHash().equals("document-hash")
        ));
        verify(otcService).issueAndSendOtc(any(Member.class));
        verify(identityDocumentService).createIdentityDocument(IdentityDocumentType.HKID, "A123456(8)", null);
    }

    @Test
    void duplicateIdentityDocumentDoesNotCreateMemberOrIssueCode() {
        MemberRepository memberRepository = mock(MemberRepository.class);
        IdentityDocumentService identityDocumentService = mock(IdentityDocumentService.class);
        HashService hashService = mock(HashService.class);
        OtcService otcService = mock(OtcService.class);
        MembershipRegistrationService service = new MembershipRegistrationService(
                memberRepository, identityDocumentService, hashService, otcService
        );
        when(memberRepository.findByEmailIgnoreCase("member@example.com")).thenReturn(Optional.empty());
        when(identityDocumentService.createIdentityDocument(
                IdentityDocumentType.HKID, "A123456(8)", null
        )).thenThrow(new ResponseStatusException(org.springframework.http.HttpStatus.CONFLICT));

        assertThrows(ResponseStatusException.class, () -> service.register(validRequest()));

        verify(memberRepository, never()).save(any(Member.class));
        verifyNoInteractions(otcService);
    }

    private MembershipRegistrationRequest validRequest() {
        return new MembershipRegistrationRequest(
                "Test", "Member", "member@example.com", "12345678",
                IdentityDocumentType.HKID, "A123456(8)", null, "secure-password"
        );
    }
}
