package hk.edu.sehs4701.cissas.security;

import hk.edu.sehs4701.cissas.entity.IdentityDocument;
import hk.edu.sehs4701.cissas.enums.IdentityDocumentType;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;
import java.util.UUID;

@Service
public class HashService {
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    private final String dummyPasswordHash = passwordEncoder.encode(UUID.randomUUID().toString());

    @Value("${otc.code.hash.pepper}")
    private String otcCodeHashPepper;

    @Value("${identity-document.hash.pepper:${IDENTITY_DOCUMENT_HASH_PEPPER:}}")
    private String identityDocumentHashPepper;

    public String hashPassword(String password) {
        if (password == null || password.isBlank()) {
            throw new IllegalArgumentException("Password must not be blank");
        }

        return passwordEncoder.encode(password);
    }

    public boolean verifyPassword(String password, String passwordHash) {
        if (password == null || password.isBlank()) {
            return false;
        }

        if (passwordHash == null) {
            passwordEncoder.matches(password, dummyPasswordHash);
            return false;
        }
        return passwordEncoder.matches(password, passwordHash);
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

        if (identityDocumentHashPepper == null || identityDocumentHashPepper.isBlank()) {
            throw new IllegalStateException("Property identity-document.hash.pepper must be set and not blank");
        }

        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(identityDocumentHashPepper.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            String issuingCountry = identityDocument.getIssuingCountry();
            String normalizedCountry = documentType == IdentityDocumentType.HKID || issuingCountry == null
                    ? ""
                    : issuingCountry.trim().toUpperCase(Locale.ROOT);
            String hashInput = documentType.name() + '\0' + normalizedCountry + '\0' + normalizedNumber;
            return HexFormat.of().formatHex(mac.doFinal(hashInput.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new IllegalStateException("Unable to hash identity document number", e);
        }
    }

    public String hashRateLimitKey(String rateLimitKey) {
        if (rateLimitKey == null || rateLimitKey.isBlank()) {
            throw new IllegalArgumentException("Rate limit key must not be blank");
        }
        if (identityDocumentHashPepper == null || identityDocumentHashPepper.isBlank()) {
            throw new IllegalStateException("Property identity-document.hash.pepper must be set and not blank");
        }

        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(identityDocumentHashPepper.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(
                    ("registration-rate-limit\0" + rateLimitKey).getBytes(StandardCharsets.UTF_8)
            ));
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new IllegalStateException("Unable to hash registration source address", e);
        }
    }

    private String normalizeDocumentNumber(String number, IdentityDocumentType type) {
        return switch (type) {
            case HKID -> number.trim().toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]", "");
            case PASSPORT -> number.trim().toUpperCase(Locale.ROOT);
            case OTHER -> number.trim();
        };
    }

    public String hashOtcCode(String otcCode) {
        if (otcCode == null || otcCode.isBlank()) {
            throw new IllegalArgumentException("OTC code must not be blank");
        }

        if (otcCodeHashPepper == null || otcCodeHashPepper.isBlank()) {
            throw new IllegalStateException("Property otc.code.hash.pepper must be set and not blank");
        }

        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(otcCodeHashPepper.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(otcCode.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new IllegalStateException("Unable to hash OTC code", e);
        }
    }

    public boolean verifyOtcCode(String rawOtcCode, String hashedOtcCode) {
        return hashOtcCode(rawOtcCode).equals(hashedOtcCode);
    }
}
