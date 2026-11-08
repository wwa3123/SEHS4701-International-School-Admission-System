package hk.edu.sehs4701.cissas.service;

import hk.edu.sehs4701.cissas.dto.request.MembershipEmailRequest;
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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MembershipRegistrationServiceTest {
    private final MemberRepository members = mock(MemberRepository.class);
    private final IdentityDocumentService documents = mock(IdentityDocumentService.class);
    private final HashService hashes = mock(HashService.class);
    private final OtcService codes = mock(OtcService.class);
    private final MembershipRegistrationService service = new MembershipRegistrationService(members, documents, hashes, codes);

    @Test
    void registrationStoresOnlyEmailAndSendsCode() {
        when(members.findByEmailIgnoreCase("member@example.com")).thenReturn(Optional.empty());
        when(members.save(any(Member.class))).thenAnswer(invocation -> invocation.getArgument(0));
        service.register(new MembershipEmailRequest(" Member@example.com "));
        verify(members).save(argThat(member -> member.getEmail().equals("member@example.com")
                && member.getAccountStatus() == AccountStatus.UNVERIFIED
                && member.getPasswordHash() == null && member.getFirstName() == null
                && member.getLastName() == null && member.getIdentityDocuments().isEmpty()));
        verify(codes).issueAndSendOtc(any(Member.class));
        verifyNoInteractions(documents, hashes);
    }

    @Test
    void pendingRegistrationResendsWithoutChangingDetails() {
        Member member = pendingMember();
        member.setPasswordHash("old-hash");
        when(members.findByEmailIgnoreCase("member@example.com")).thenReturn(Optional.of(member));
        service.register(new MembershipEmailRequest("member@example.com"));
        assertEquals("old-hash", member.getPasswordHash());
        verify(codes).throttleAndResendOtc(member);
        verifyNoInteractions(documents, hashes);
    }

    @Test
    void verifyDoesNotActivateAccount() {
        Member member = pendingMember();
        when(members.findByEmailIgnoreCase("member@example.com")).thenReturn(Optional.of(member));
        service.verifyEmail("member@example.com", "123456");
        verify(codes).verifyOtc(1L, "123456");
        assertEquals(AccountStatus.UNVERIFIED, member.getAccountStatus());
        assertNull(member.getEmailVerifiedAt());
    }

    @Test
    void completionReplacesOldCredentialsOnlyAfterVerification() {
        Member member = pendingMember();
        member.setPasswordHash("old-hash");
        when(members.findByEmailIgnoreCase("member@example.com")).thenReturn(Optional.of(member));
        IdentityDocument document = new IdentityDocument(IdentityDocumentType.HKID, "A123456(3)", "HK", "hash");
        when(documents.createIdentityDocument(IdentityDocumentType.HKID, "A123456(3)", null)).thenReturn(document);
        when(hashes.hashPassword("secure-password")).thenReturn("new-hash");

        assertThrows(ResponseStatusException.class, () -> service.completeRegistration("other@example.com", request()));
        service.completeRegistration("member@example.com", request());
        assertEquals("new-hash", member.getPasswordHash());
        assertEquals("Test", member.getFirstName());
        assertEquals(AccountStatus.ACTIVE, member.getAccountStatus());
        assertNotNull(member.getEmailVerifiedAt());
        assertSame(document, member.getIdentityDocuments().getFirst());
    }

    @Test
    void completionRejectsLegacyIdentityDocumentsWithoutModifyingThem() {
        Member member = pendingMember();
        member.addIdentityDocument(new IdentityDocument(IdentityDocumentType.HKID, "A123456(3)", "HK", "hash"));
        when(members.findByEmailIgnoreCase("member@example.com")).thenReturn(Optional.of(member));
        assertThrows(ResponseStatusException.class, () -> service.completeRegistration("member@example.com", request()));
        assertEquals(AccountStatus.UNVERIFIED, member.getAccountStatus());
        verifyNoInteractions(documents, hashes);
    }

    @Test
    void duplicateDocumentDoesNotActivateAccount() {
        Member member = pendingMember();
        when(members.findByEmailIgnoreCase("member@example.com")).thenReturn(Optional.of(member));
        when(documents.createIdentityDocument(any(), any(), any()))
                .thenThrow(new ResponseStatusException(org.springframework.http.HttpStatus.CONFLICT));
        assertThrows(ResponseStatusException.class, () -> service.completeRegistration("member@example.com", request()));
        assertEquals(AccountStatus.UNVERIFIED, member.getAccountStatus());
        verify(members, never()).save(any());
    }

    private Member pendingMember() {
        Member member = new Member();
        member.setId(1L);
        member.setEmail("member@example.com");
        member.setAccountStatus(AccountStatus.UNVERIFIED);
        return member;
    }

    private MembershipRegistrationRequest request() {
        return new MembershipRegistrationRequest("Test", "Member", "member@example.com", "12345678",
                IdentityDocumentType.HKID, "A123456(3)", null, "secure-password");
    }
}
