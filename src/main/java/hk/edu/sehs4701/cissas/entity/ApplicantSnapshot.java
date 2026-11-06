package hk.edu.sehs4701.cissas.entity;

import hk.edu.sehs4701.cissas.enums.IdentityDocumentType;
import hk.edu.sehs4701.cissas.security.AesEncryptionConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.util.Locale;

@Embeddable
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ApplicantSnapshot {
    @Column(name = "submitted_applicant_name", nullable = false, updatable = false, length = 120)
    private String fullName;

    @Column(name = "submitted_name_hash", nullable = false, updatable = false, length = 32)
    private byte[] nameHash;

    @Column(name = "submitted_date_of_birth", nullable = false, updatable = false)
    private LocalDate dateOfBirth;

    @Column(name = "submitted_gender", nullable = false, updatable = false, length = 20)
    private String gender;

    @Column(name = "submitted_phone", nullable = false, updatable = false, length = 25)
    private String phone;

    @Column(name = "submitted_previous_school", nullable = false, updatable = false, length = 150)
    private String previousSchool;

    @Enumerated(EnumType.STRING)
    @Column(name = "submitted_document_type", nullable = false, updatable = false, length = 32)
    private IdentityDocumentType documentType;

    @Convert(converter = AesEncryptionConverter.class)
    @Column(name = "submitted_document_number", nullable = false, updatable = false, length = 4096)
    private String documentNumber;

    @Column(name = "submitted_document_issuing_country", updatable = false, length = 2)
    private String issuingCountry;

    public ApplicantSnapshot(String fullName, LocalDate dateOfBirth, String gender, String phone,
                             String previousSchool, IdentityDocumentType documentType,
                             String documentNumber, String issuingCountry) {
        this.fullName = fullName;
        this.nameHash = hashName(fullName);
        this.dateOfBirth = dateOfBirth;
        this.gender = gender;
        this.phone = phone;
        this.previousSchool = previousSchool;
        this.documentType = documentType;
        this.documentNumber = documentNumber;
        this.issuingCountry = issuingCountry;
    }

    public static String normalizeName(String name) {
        return name.trim().replaceAll("\\s+", " ").toUpperCase(Locale.ROOT);
    }

    public static byte[] hashName(String name) {
        try {
            return MessageDigest.getInstance("SHA-256")
                    .digest(normalizeName(name).getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
