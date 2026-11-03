package hk.edu.cis.cissas.repository;

import hk.edu.cis.cissas.entity.OneTimeCode;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OneTimeCodeRepository extends JpaRepository<OneTimeCode, Long> {
    Optional<OneTimeCode> findFirstByMemberIdOrderByCreatedAtDesc(Long memberId);
}
