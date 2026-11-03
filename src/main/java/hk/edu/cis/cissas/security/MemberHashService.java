package hk.edu.cis.cissas.security;

import hk.edu.cis.cissas.entity.IdentityDocument;
import hk.edu.cis.cissas.enums.IdentityDocumentType;
import org.springframework.stereotype.Service;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;

@Service
public class MemberHashService {
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    public String hashPassword(String password) {
        if (password == null || password.isBlank()) {
            throw new IllegalArgumentException("Password must not be blank");
        }

        return passwordEncoder.encode(password);
    }

    public String generateDocumentNumberHash(IdentityDocument identityDocument) {
        String documentNumber = identityDocument.getDocumentNumber();
        IdentityDocumentType documentType = identityDocument.getDocumentType();

        if (documentNumber == null || documentNumber.isBlank()) {
            throw new IllegalStateException("Identity document number must not be blank");
        }

        if (documentType == null) {
            throw new IllegalStateException("Identity document type must not be null");
        }
        String normalizedNumber = normalizeDocumentNumber(documentNumber, documentType);
        if (normalizedNumber.isBlank()) {
            throw new IllegalStateException("Identity document number must contain a non-whitespace character");
        }

        String pepper = System.getenv("IDENTITY_DOCUMENT_HASH_PEPPER");
        if (pepper == null || pepper.isBlank()) {
            throw new IllegalStateException("Environment variable IDENTITY_DOCUMENT_HASH_PEPPER must be set and not blank");
        }

        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(pepper.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            String issuingCountry = identityDocument.getIssuingCountry();
            String normalizedCountry = issuingCountry == null
                    ? ""
                    : issuingCountry.trim().toUpperCase(Locale.ROOT);
            String hashInput = documentType.name() + '\0' + normalizedCountry + '\0' + normalizedNumber;
            return HexFormat.of().formatHex(mac.doFinal(hashInput.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new IllegalStateException("Unable to hash identity document number", e);
        }
    }

    private String normalizeDocumentNumber(String number, IdentityDocumentType type) {
        return switch (type) {
            case HKID -> number.trim().toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]", "");
            case PASSPORT -> number.trim().toUpperCase(Locale.ROOT);
            case OTHER -> number.trim();
        };
    }
}
