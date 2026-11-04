package hk.edu.sehs4701.cissas.repository;

import hk.edu.sehs4701.cissas.enums.EmailDeliveryStatus;
import hk.edu.sehs4701.cissas.entity.EmailLog;
import hk.edu.sehs4701.cissas.enums.EmailTemplateType;
import java.util.List;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EmailLogRepository extends JpaRepository<EmailLog, Long> {
    List<EmailLog> findAllByStatusAndRetryCountLessThanOrderByCreatedAtAsc(
            EmailDeliveryStatus status, int maxRetries
    );

    List<EmailLog> findAllByMemberIdOrderByCreatedAtDesc(Long memberId);

    @Modifying
    @Query("""
            update EmailLog e set e.status = :failedStatus, e.errorMessage = :message
            where e.templateType = :templateType and e.status = :pendingStatus
            """)
    int failPendingEmailsByTemplateType(
            @Param("templateType") EmailTemplateType templateType,
            @Param("pendingStatus") EmailDeliveryStatus pendingStatus,
            @Param("failedStatus") EmailDeliveryStatus failedStatus,
            @Param("message") String message
    );

    @Modifying
    @Query("update EmailLog e set e.body = :body where e.templateType = :templateType")
    int redactBodiesByTemplateType(
            @Param("templateType") EmailTemplateType templateType,
            @Param("body") String body
    );
}
