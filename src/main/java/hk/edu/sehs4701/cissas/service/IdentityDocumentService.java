package hk.edu.sehs4701.cissas.service;

import hk.edu.sehs4701.cissas.entity.IdentityDocument;
import hk.edu.sehs4701.cissas.enums.IdentityDocumentType;
import hk.edu.sehs4701.cissas.repository.IdentityDocumentRepository;
import hk.edu.sehs4701.cissas.security.HashService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.Locale;

@Service
public class IdentityDocumentService {
    private final IdentityDocumentRepository identityDocumentRepository;
    private final HashService hashService;

    public IdentityDocumentService(
            IdentityDocumentRepository identityDocumentRepository,
            HashService hashService) {
        this.identityDocumentRepository = identityDocumentRepository;
        this.hashService = hashService;
    }

    public IdentityDocument validateAndCreate(
            IdentityDocumentType documentType,
            String documentNumber,
            String issuingCountry) {
        if (documentType == null || documentNumber == null || documentNumber.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Identity document details are required");
        }
        documentNumber = documentNumber.trim();
        issuingCountry = issuingCountry == null ? null : issuingCountry.trim().toUpperCase(Locale.ROOT);
        if (documentType == IdentityDocumentType.HKID) {
            documentNumber = documentNumber.toUpperCase(Locale.ROOT);
            if (!documentNumber.matches("^[A-Z]{1,2}[0-9]{6}\\([0-9A]\\)$")) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid HKID format");
            }
            issuingCountry = "HK";
        } else if (issuingCountry == null || !issuingCountry.matches("^[A-Z]{2}$")) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "A two-letter issuing country is required for this document type"
            );
        }

        IdentityDocument identityDocument = new IdentityDocument();
        identityDocument.setDocumentType(documentType);
        identityDocument.setDocumentNumber(documentNumber);
        identityDocument.setIssuingCountry(issuingCountry);
        String documentHash = hashService.generateDocumentNumberHash(identityDocument);
        if (identityDocumentRepository.existsByDocumentNumberHash(documentHash)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Identity document is already linked to an account");
        }
        identityDocument.setDocumentNumberHash(documentHash);
        return identityDocument;
    }
}
