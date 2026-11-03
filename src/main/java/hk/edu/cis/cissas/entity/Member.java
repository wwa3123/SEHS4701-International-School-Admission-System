package hk.edu.cis.cissas.entity;

import hk.edu.cis.cissas.enums.AccountStatus;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import io.hypersistence.utils.hibernate.id.Tsid;
import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

@Entity
@Getter
@Setter
@NoArgsConstructor
public class Member {
    @Id
    @Tsid
    @Column(name = "member_id")
    private Long id;

    @Column(nullable = false, length = 150, unique = true)
    private String username;

    @Column(unique = true, nullable = false, length = 150)
    private String email;

    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    @Column(name = "first_name", nullable = false, length = 120)
    private String firstName;

    @Column(name = "last_name", nullable = false, length = 120)
    private String lastName;

    @Column(name = "phone_number", length = 25)
    private String phoneNumber;

    @Column(name = "date_of_birth")
    private Date dateOfBirth;

    @Column(name = "address",length = 300)
    private String address;

    @Enumerated(EnumType.STRING)
    @Column(
            name = "account_status",
            nullable = false,
            columnDefinition = "ENUM('UNVERIFIED', 'ACTIVE', 'SUSPENDED') DEFAULT 'UNVERIFIED'"
    )
    private AccountStatus accountStatus = AccountStatus.UNVERIFIED;

    @Column(name = "email_verified_at")
    private LocalDateTime emailVerifiedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @OneToMany(mappedBy = "member", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<IdentityDocument> identityDocuments = new ArrayList<>();

    public void addIdentityDocument(IdentityDocument identityDocument) {
        identityDocuments.add(identityDocument);
        identityDocument.setMember(this);
    }

    public void removeIdentityDocument(IdentityDocument identityDocument) {
        identityDocuments.remove(identityDocument);
        identityDocument.setMember(null);
    }
}