package hk.edu.sehs4701.cissas.repository;

import hk.edu.sehs4701.cissas.entity.IdentityReviewLock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface IdentityReviewLockRepository extends JpaRepository<IdentityReviewLock, Long> {
    @Modifying
    @Query(value = "INSERT INTO identity_review_lock (lock_id) VALUES (1) "
            + "ON DUPLICATE KEY UPDATE lock_id = lock_id", nativeQuery = true)
    void acquire();

    @Modifying
    @Query(value = "INSERT INTO identity_review_lock (lock_id) VALUES (2) "
            + "ON DUPLICATE KEY UPDATE lock_id = lock_id", nativeQuery = true)
    void acquireAccountLock();
}
