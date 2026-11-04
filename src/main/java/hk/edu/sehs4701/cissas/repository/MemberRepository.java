package hk.edu.sehs4701.cissas.repository;

import hk.edu.sehs4701.cissas.entity.Member;
import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

public interface MemberRepository extends JpaRepository<Member, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Member> findByEmailIgnoreCase(String email);
}
