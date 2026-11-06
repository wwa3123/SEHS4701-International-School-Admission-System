package hk.edu.sehs4701.cissas.service;

import hk.edu.sehs4701.cissas.entity.IdentityDocument;
import hk.edu.sehs4701.cissas.enums.IdentityDocumentType;
import hk.edu.sehs4701.cissas.repository.IdentityDocumentRepository;
import hk.edu.sehs4701.cissas.security.HashService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.Locale;
import java.util.Optional;

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

    public void validateIdentityDocument(
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
            if (!validateHKIDChecksum(documentNumber)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid HKID format");
            }
            issuingCountry = "HK";
        } else if (issuingCountry == null || !issuingCountry.matches("^[A-Z]{2}$")) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "A two-letter issuing country is required for this document type"
            );
        }
    }

    public IdentityDocument createIdentityDocument(
            IdentityDocumentType documentType,
            String documentNumber,
            String issuingCountry) {
        IdentityDocument identityDocument = normalizeAndValidate(documentType, documentNumber, issuingCountry);
        String documentHash = hashService.generateDocumentNumberHash(identityDocument);

        if (identityDocumentRepository.existsByDocumentNumberHash(documentHash)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Identity document is already linked to an account");
        }

        return new IdentityDocument(identityDocument.getDocumentType(), identityDocument.getDocumentNumber(),
                identityDocument.getIssuingCountry(), documentHash);
    }

    public String getDocumentNumberHash(
            IdentityDocumentType documentType,
            String documentNumber,
            String issuingCountry) {
        IdentityDocument identityDocument = normalizeAndValidate(documentType, documentNumber, issuingCountry);
        return hashService.generateDocumentNumberHash(identityDocument);
    }

    public Optional<IdentityDocument> findByHashForUpdate(String documentHash) {
        return identityDocumentRepository.findByDocumentNumberHash(documentHash);
    }

    private IdentityDocument normalizeAndValidate(
            IdentityDocumentType documentType,
            String documentNumber,
            String issuingCountry) {
        validateIdentityDocument(documentType, documentNumber, issuingCountry);

        String normalizedNumber = documentNumber.trim();
        String normalizedCountry = issuingCountry == null
                ? null
                : issuingCountry.trim().toUpperCase(Locale.ROOT);

        if (documentType == IdentityDocumentType.HKID) {
            normalizedNumber = normalizedNumber.toUpperCase(Locale.ROOT);
            normalizedCountry = "HK";
        } else if (documentType == IdentityDocumentType.PASSPORT) {
            normalizedNumber = normalizedNumber.toUpperCase(Locale.ROOT);
        }

        return new IdentityDocument(documentType, normalizedNumber, normalizedCountry, null);
    }


    private boolean validateHKIDChecksum(String documentNumber) {
        /* This regex checks for an HKID-shaped value:
        * ^ and $: require the entire string to match
        * [A-Z]{1,2}: one or two uppercase letters
        * [0-9]{6}: six digits
        * \\([0-9A]\\): a digit or 'A' enclosed in parentheses
        *
        * Correct examples: A123456(3), AB123456(9)
        * Wrong examples: A1234567, A123456(8), A12345(3), AA123456(7)
        * */
        if (!documentNumber.matches("^[A-Z]{1,2}[0-9]{6}\\([0-9A]\\)$")) {
            return false;
        }

        String cleaned = documentNumber.replaceAll("[()]", "");
        char checksumChar = cleaned.charAt(cleaned.length() - 1);
        String numberPart = cleaned.substring(0, cleaned.length() - 1);

        int sum = numberPart.length() == 7 ? 36 * 9 : 0;
        for (int i = 0; i < numberPart.length(); i++) {
            char c = numberPart.charAt(i);
            int value;
            if (Character.isDigit(c)) {
                value = Character.getNumericValue(c);
            } else if (Character.isLetter(c)) {
                value = c - 'A' + 10; // A=10, B=11, ..., Z=35
            } else {
                return false; // Invalid character
            }
            sum += value * (numberPart.length() + 1 - i);
        }

        int remainder = sum % 11;
        char expectedChecksumChar;
        if (remainder == 0) {
            expectedChecksumChar = '0';
        } else if (remainder == 1) {
            expectedChecksumChar = 'A';
        } else {
            expectedChecksumChar = (char) ('0' + (11 - remainder));
        }

        return checksumChar == expectedChecksumChar;
    }
}
