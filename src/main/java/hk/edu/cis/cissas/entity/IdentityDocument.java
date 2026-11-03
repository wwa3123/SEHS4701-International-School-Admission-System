package hk.edu.cis.cissas.entity;

import hk.edu.cis.cissas.enums.IdentityDocumentType;
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
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

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
}
