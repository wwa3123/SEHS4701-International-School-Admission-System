package hk.edu.cis.cissas.model.entity;

import hk.edu.cis.cissas.model.enums.AccountStatus;
import hk.edu.cis.cissas.model.enums.AccountRole;
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

    @Column(unique = true, nullable = false)
    private String email;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(name = "first_name", nullable = false)
    private String firstName;

    @Column(name = "last_name", nullable = false)
    private String lastName;

    @Column(name = "phone_number")
    private String phoneNumber;

    @Column(name = "date_of_birth")
    private Date dateOfBirth;

    @Column(name = "address")
    private String address;

    @Enumerated(EnumType.STRING)
    @Column(name = "role",
            nullable = false,
            columnDefinition = "ENUM('ROLE_MEMBER', 'ROLE_ADMIN') DEFAULT 'ROLE_MEMBER'"
    )
    private AccountRole accountRole = AccountRole.ROLE_MEMBER;

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