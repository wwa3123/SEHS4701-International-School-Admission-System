package hk.edu.sehs4701.cissas.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "identity_review_lock")
public class IdentityReviewLock {
    @Id
    @Column(name = "lock_id")
    private Long id;
}
