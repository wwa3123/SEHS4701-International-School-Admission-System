package hk.edu.cis.cissas.repository;

import hk.edu.cis.cissas.entity.Admin;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AdminRepository extends JpaRepository<Admin, Long> {
    Optional<Admin> findByEmailIgnoreCase(String email);
}