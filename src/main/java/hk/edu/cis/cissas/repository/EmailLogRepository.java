package hk.edu.cis.cissas.repository;

import hk.edu.cis.cissas.enums.EmailDeliveryStatus;
import hk.edu.cis.cissas.entity.EmailLog;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EmailLogRepository extends JpaRepository<EmailLog, Long> {
    List<EmailLog> findAllByStatusAndRetryCountLessThanOrderByCreatedAtAsc(
            EmailDeliveryStatus status, int maxRetries
    );

    List<EmailLog> findAllByMemberIdOrderByCreatedAtDesc(Long memberId);
}
