package hk.edu.sehs4701.cissas.repository;

import hk.edu.sehs4701.cissas.entity.OneTimeCode;
import hk.edu.sehs4701.cissas.enums.OneTimeCodePurpose;
import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

public interface OneTimeCodeRepository extends JpaRepository<OneTimeCode, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<OneTimeCode> findFirstByMemberIdAndPurposeOrderByCreatedAtDescIdDesc(
            Long memberId, OneTimeCodePurpose purpose);

    long countByMemberIdAndPurposeAndCreatedAtAfter(Long memberId, OneTimeCodePurpose purpose,
                                                    java.time.LocalDateTime createdAt);
}
