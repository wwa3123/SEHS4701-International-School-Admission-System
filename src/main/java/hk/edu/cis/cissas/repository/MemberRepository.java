package hk.edu.cis.cissas.repository;

import hk.edu.cis.cissas.entity.Member;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MemberRepository extends JpaRepository<Member, Long> {
    boolean existsByEmailIgnoreCase(String email);
    Optional<Member> findByEmailIgnoreCase(String email);
}
