package hk.edu.cis.cissas.model.entity;

import hk.edu.cis.cissas.model.enums.IdentityDocumentType;
import hk.edu.cis.cissas.security.AesEncryptionConverter;
import io.hypersistence.utils.hibernate.id.Tsid;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;

@Entity
@Table(name = "identity_document")
@Getter
@Setter
@NoArgsConstructor
public class IdentityDocument {
    @Id
    @Tsid
    @Column(name = "identity_document_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    @Enumerated(EnumType.STRING)
    @Column(name = "document_type", nullable = false, length = 32)
    private IdentityDocumentType documentType;

    @Convert(converter = AesEncryptionConverter.class)
    @Column(name = "encrypted_document_number", nullable = false, length = 512)
    private String documentNumber;

    @Column(name = "document_number_hash", nullable = false, unique = true, length = 64)
    private String documentNumberHash;

    @Column(name = "issuing_country", length = 2)
    private String issuingCountry;

    @PrePersist
    @PreUpdate
    private void generateDocumentNumberHash() {
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

            // Default pepper value for development/testing
            pepper = "b37847011eb98a19b7dfc0ac682d38427b55f4139461170ad47ee1dccef61f49";
            System.err.println("Warning: Using default pepper value for identity document hashing. This is not secure for production.");

            //throw new IllegalStateException("IDENTITY_DOCUMENT_HASH_PEPPER environment variable is required");
        }

        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(pepper.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            String normalizedCountry = issuingCountry == null
                    ? ""
                    : issuingCountry.trim().toUpperCase(Locale.ROOT);
            String hashInput = documentType.name() + '\0' + normalizedCountry + '\0' + normalizedNumber;
            documentNumberHash = HexFormat.of().formatHex(mac.doFinal(hashInput.getBytes(StandardCharsets.UTF_8)));
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
