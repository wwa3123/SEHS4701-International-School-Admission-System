package hk.edu.sehs4701.cissas.entity;

import hk.edu.sehs4701.cissas.enums.IdentityDocumentType;
import hk.edu.sehs4701.cissas.security.AesEncryptionConverter;
import io.hypersistence.utils.hibernate.id.Tsid;
import org.hibernate.annotations.Check;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.AccessLevel;

@Entity
@Table(
        name = "identity_document",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_identity_document_applicant_type", columnNames = {"applicant_id", "document_type"}),
                @UniqueConstraint(name = "uk_identity_document_member_type", columnNames = {"member_id", "document_type"})
        }
)
@Check(constraints = "applicant_id IS NOT NULL OR member_id IS NOT NULL")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class IdentityDocument {
    @Id
    @Tsid
    @Column(name = "identity_document_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "applicant_id")
    private Applicant applicant;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id")
    private Member member;

    @Enumerated(EnumType.STRING)
    @Column(name = "document_type", nullable = false, updatable = false, length = 32)
    private IdentityDocumentType documentType;

    @Convert(converter = AesEncryptionConverter.class)
    @Column(name = "encrypted_document_number", nullable = false, updatable = false, length = 8192)
    private String documentNumber;

    @Column(name = "document_number_hash", nullable = false, updatable = false, unique = true, length = 64)
    private String documentNumberHash;

    @Column(name = "issuing_country", updatable = false, length = 2)
    private String issuingCountry;

    public IdentityDocument(IdentityDocumentType documentType, String documentNumber,
                            String issuingCountry, String documentNumberHash) {
        this.documentType = documentType;
        this.documentNumber = documentNumber;
        this.issuingCountry = issuingCountry;
        this.documentNumberHash = documentNumberHash;
    }

    public void attachToApplicant(Applicant applicant) {
        if (this.applicant != null || applicant == null || applicant.getMember() == null
                || (member != null && member != applicant.getMember()
                && (member.getId() == null || !member.getId().equals(applicant.getMember().getId())))) {
            throw new IllegalStateException("Identity document cannot be linked to this applicant");
        }
        this.applicant = applicant;
    }

    public void attachToMember(Member member) {
        if (this.member != null || member == null || applicant != null) {
            throw new IllegalStateException("Identity document cannot be linked to this member");
        }
        this.member = member;
    }
}
