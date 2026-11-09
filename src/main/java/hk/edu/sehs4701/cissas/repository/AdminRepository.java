package hk.edu.sehs4701.cissas.repository;

import hk.edu.sehs4701.cissas.entity.Admin;
import hk.edu.sehs4701.cissas.enums.ActorRole;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AdminRepository extends JpaRepository<Admin, Long> {
    Optional<Admin> findByEmailIgnoreCase(String email);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Admin> findLockedById(Long id);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Admin> findLockedByEmailIgnoreCase(String email);
    boolean existsByRole(ActorRole role);
    List<Admin> findAllByRoleOrderByEmailAsc(ActorRole role);
}