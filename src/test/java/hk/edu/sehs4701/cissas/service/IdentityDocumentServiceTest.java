package hk.edu.sehs4701.cissas.service;

import hk.edu.sehs4701.cissas.enums.IdentityDocumentType;
import hk.edu.sehs4701.cissas.repository.IdentityDocumentRepository;
import hk.edu.sehs4701.cissas.security.HashService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class IdentityDocumentServiceTest {
    private final IdentityDocumentRepository repository = mock(IdentityDocumentRepository.class);
    private final HashService hashService = mock(HashService.class);
    private final IdentityDocumentService service = new IdentityDocumentService(repository, hashService);

    @Test
    void normalizesHkidAndCreatesDocumentWithHash() {
        when(hashService.generateDocumentNumberHash(any())).thenReturn("document-hash");

        var document = service.validateAndCreate(IdentityDocumentType.HKID, " a123456(7) ", null);

        assertEquals(IdentityDocumentType.HKID, document.getDocumentType());
        assertEquals("A123456(7)", document.getDocumentNumber());
        assertEquals("HK", document.getIssuingCountry());
        assertEquals("document-hash", document.getDocumentNumberHash());
        verify(repository).existsByDocumentNumberHash("document-hash");
    }

    @Test
    void rejectsInvalidHkid() {
        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> service.validateAndCreate(IdentityDocumentType.HKID, "invalid", null)
        );

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
        verifyNoInteractions(repository, hashService);
    }

    @Test
    void rejectsDuplicateDocument() {
        when(hashService.generateDocumentNumberHash(any())).thenReturn("document-hash");
        when(repository.existsByDocumentNumberHash("document-hash")).thenReturn(true);

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> service.validateAndCreate(IdentityDocumentType.PASSPORT, "A123", "hk")
        );

        assertEquals(HttpStatus.CONFLICT, exception.getStatusCode());
    }

    @Test
    void requiresIssuingCountryForNonHkidDocuments() {
        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> service.validateAndCreate(IdentityDocumentType.PASSPORT, "A123", "HKG")
        );

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
        verifyNoInteractions(repository, hashService);
    }
}
