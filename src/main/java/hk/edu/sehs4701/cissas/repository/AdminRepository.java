package hk.edu.sehs4701.cissas.repository;

import hk.edu.sehs4701.cissas.entity.Admin;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AdminRepository extends JpaRepository<Admin, Long> {
    Optional<Admin> findByEmailIgnoreCase(String email);
}