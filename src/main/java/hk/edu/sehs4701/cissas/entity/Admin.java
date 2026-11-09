package hk.edu.sehs4701.cissas.entity;

import hk.edu.sehs4701.cissas.enums.ActorRole;
import io.hypersistence.utils.hibernate.id.Tsid;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;


@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(
        name = "admin",
        uniqueConstraints = @UniqueConstraint(name = "admin_email", columnNames = "email")
)
public class Admin {
    @Id
    @Tsid
    private Long id;

    @Column(nullable = false, length = 150, unique = true)
    private String email;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(name = "first_name", nullable = false, length = 120)
    private String firstName;

    @Column(name = "last_name", nullable = false, length = 120)
    private String lastName;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @Column(name = "credential_version", nullable = false)
    private long credentialVersion = 0;

    @Column(name = "password_change_required", nullable = false)
    private boolean passwordChangeRequired = false;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32, columnDefinition = "varchar(32) default 'ROLE_ADMIN'")
    private ActorRole role = ActorRole.ROLE_ADMIN;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

}
