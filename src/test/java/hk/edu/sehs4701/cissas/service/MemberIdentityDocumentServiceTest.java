package hk.edu.sehs4701.cissas.service;

import hk.edu.sehs4701.cissas.dto.request.MemberIdentityDocumentRequest;
import hk.edu.sehs4701.cissas.dto.response.MemberIdentityDocument;
import hk.edu.sehs4701.cissas.entity.IdentityDocument;
import hk.edu.sehs4701.cissas.entity.Member;
import hk.edu.sehs4701.cissas.enums.IdentityDocumentType;
import hk.edu.sehs4701.cissas.repository.IdentityDocumentRepository;
import hk.edu.sehs4701.cissas.repository.IdentityReviewLockRepository;
import hk.edu.sehs4701.cissas.repository.MemberRepository;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;
import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MemberIdentityDocumentServiceTest {
    private final MemberRepository members = mock(MemberRepository.class);
    private final IdentityDocumentRepository documents = mock(IdentityDocumentRepository.class);
    private final IdentityDocumentService identityDocuments = mock(IdentityDocumentService.class);
    private final IdentityReviewLockRepository lock = mock(IdentityReviewLockRepository.class);
    private final MemberIdentityDocumentService service =
            new MemberIdentityDocumentService(members, documents, identityDocuments, lock);

    @Test
    void firstDocumentBecomesPrimaryAndAddedDocumentsAreNonPrimary() {
        Member member = new Member();
        member.setId(7L);
        when(members.findByIdForUpdate(7L)).thenReturn(Optional.of(member));
        IdentityDocument first = new IdentityDocument(IdentityDocumentType.HKID, "A123456(3)", "HK", "one");
        IdentityDocument second = new IdentityDocument(IdentityDocumentType.PASSPORT, "AB12345", "JP", "two");
        when(identityDocuments.createIdentityDocument(IdentityDocumentType.HKID, "A123456(3)", "HK"))
                .thenReturn(first);
        when(identityDocuments.createIdentityDocument(IdentityDocumentType.PASSPORT, "AB12345", "JP"))
                .thenReturn(second);

        service.add(7L, new MemberIdentityDocumentRequest(IdentityDocumentType.HKID, "A123456(3)", "HK"));
        service.add(7L, new MemberIdentityDocumentRequest(IdentityDocumentType.PASSPORT, "AB12345", "JP"));

        assertTrue(first.isPrimaryDocument());
        assertFalse(second.isPrimaryDocument());
        assertEquals(2, member.getIdentityDocuments().size());
        verify(lock, times(2)).acquire();
    }

    @Test
    void primaryCanOnlyBeChangedToOneOfTheMembersOwnDocuments() {
        Member member = new Member();
        IdentityDocument first = document(1L, "AB12345", true);
        IdentityDocument second = document(2L, "CD67890", false);
        when(members.findByIdForUpdate(7L)).thenReturn(Optional.of(member));
        when(documents.findAllByMember_IdOrderByIdAsc(7L)).thenReturn(List.of(first, second));

        service.setPrimary(7L, 2L);

        assertFalse(first.isPrimaryDocument());
        assertTrue(second.isPrimaryDocument());
        verify(documents).saveAll(List.of(first, second));

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> service.setPrimary(7L, 3L));
        assertEquals(HttpStatus.NOT_FOUND, exception.getStatusCode());
        verify(documents, times(1)).saveAll(anyIterable());
    }

    @Test
    void addingDocumentPreservesFallbackPrimaryForExistingDocuments() {
        Member member = new Member();
        IdentityDocument existing = document(1L, "AB12345", false);
        ReflectionTestUtils.setField(member, "identityDocuments", new ArrayList<>(List.of(existing)));
        when(members.findByIdForUpdate(7L)).thenReturn(Optional.of(member));
        when(documents.findAllByMember_IdOrderByIdAsc(7L)).thenReturn(List.of(existing));
        IdentityDocument added = document(2L, "CD67890", false);
        when(identityDocuments.createIdentityDocument(IdentityDocumentType.PASSPORT, "CD67890", "HK"))
                .thenReturn(added);

        service.add(7L, new MemberIdentityDocumentRequest(IdentityDocumentType.PASSPORT, "CD67890", "HK"));

        assertTrue(existing.isPrimaryDocument());
        assertFalse(added.isPrimaryDocument());
    }

    @Test
    void listingMasksNumbersAndUsesOnePrimaryDocument() {
        IdentityDocument first = document(null, "A123456(3)", false);
        IdentityDocument second = document(null, "AB12345", false);
        when(documents.findAllByMember_IdOrderByIdAsc(7L)).thenReturn(List.of(first, second));

        var result = service.list(7L);

        assertEquals("****3)", result.getFirst().maskedDocumentNumber());
        assertTrue(result.getFirst().primary());
        assertFalse(result.get(1).primary());
    }

    @Test
    void listingFullyMasksDocumentNumbersWithAtMostTwoCharacters() {
        IdentityDocument oneCharacter = new IdentityDocument(IdentityDocumentType.OTHER, "A", "HK", "one");
        IdentityDocument twoCharacters = new IdentityDocument(IdentityDocumentType.OTHER, "AB", "HK", "two");
        when(documents.findAllByMember_IdOrderByIdAsc(7L)).thenReturn(List.of(oneCharacter, twoCharacters));

        var result = service.list(7L);

        assertEquals(List.of("****", "****"), result.stream()
                .map(MemberIdentityDocument::maskedDocumentNumber).toList());
    }

    private IdentityDocument document(Long id, String number, boolean primary) {
        IdentityDocument document = new IdentityDocument(IdentityDocumentType.PASSPORT, number, "HK", "hash");
        ReflectionTestUtils.setField(document, "id", id);
        document.setPrimaryDocument(primary);
        return document;
    }
}
