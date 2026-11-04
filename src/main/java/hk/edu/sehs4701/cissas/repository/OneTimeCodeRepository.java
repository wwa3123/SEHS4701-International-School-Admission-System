package hk.edu.sehs4701.cissas.repository;

import hk.edu.sehs4701.cissas.entity.OneTimeCode;
import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

public interface OneTimeCodeRepository extends JpaRepository<OneTimeCode, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<OneTimeCode> findFirstByMemberIdOrderByCreatedAtDesc(Long memberId);
}
