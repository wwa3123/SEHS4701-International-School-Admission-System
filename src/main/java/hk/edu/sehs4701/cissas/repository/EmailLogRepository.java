package hk.edu.sehs4701.cissas.repository;

import hk.edu.sehs4701.cissas.enums.EmailDeliveryStatus;
import hk.edu.sehs4701.cissas.entity.EmailLog;
import hk.edu.sehs4701.cissas.enums.EmailTemplateType;
import java.util.List;
import java.util.Collection;
import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;

public interface EmailLogRepository extends JpaRepository<EmailLog, Long> {
    @Query("select e.application.id from EmailLog e where e.id = :id")
    Optional<Long> findApplicationId(Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<EmailLog> findLockedById(Long id);

    List<EmailLog> findAllByStatusAndTemplateTypeIn(EmailDeliveryStatus status, Collection<EmailTemplateType> types);

    Slice<EmailLog> findAllByStatusAndTemplateTypeInOrderByIdAsc(
            EmailDeliveryStatus status, Collection<EmailTemplateType> types, Pageable pageable);

    Slice<EmailLog> findAllByStatusInAndTemplateTypeAndIdGreaterThanOrderByIdAsc(
            Collection<EmailDeliveryStatus> statuses, EmailTemplateType type, Long afterId, Pageable pageable);

    @Query("""
            select e.id from EmailLog e
            where e.application is not null and e.status in :statuses and e.templateType in :types
              and e.id > :afterId
              and not exists (select earlier.id from EmailLog earlier
                  where earlier.application = e.application and earlier.id < e.id
                    and earlier.status in :statuses and earlier.templateType in :types)
            order by e.id
            """)
    Slice<Long> findUnsentApplicationEmailHeads(
            Collection<EmailDeliveryStatus> statuses, Collection<EmailTemplateType> types, Long afterId, Pageable pageable);

    @Query("select min(e.id) from EmailLog e where e.application.id = :applicationId and e.templateType in :types")
    Optional<Long> findFirstApplicationEmailId(Long applicationId, Collection<EmailTemplateType> types);

    Optional<EmailLog> findFirstByApplication_IdAndTemplateTypeInAndStatusInOrderByIdAsc(
            Long applicationId, Collection<EmailTemplateType> types, Collection<EmailDeliveryStatus> statuses);

    boolean existsByApplication_IdAndIdGreaterThanAndTemplateTypeInAndStatus(
            Long applicationId, Long id, Collection<EmailTemplateType> types, EmailDeliveryStatus status);

    @Transactional
    @Modifying
    @Query("""
            update EmailLog e set e.status = :failedStatus, e.errorMessage = :message
            where e.id = :id and e.status = :pendingStatus
            """)
    void failIfPending(@Param("id") Long id, @Param("pendingStatus") EmailDeliveryStatus pendingStatus,
                      @Param("failedStatus") EmailDeliveryStatus failedStatus, @Param("message") String message);

    @Modifying
    @Query("""
            update EmailLog e set e.status = :failedStatus, e.errorMessage = :message
            where e.templateType = :templateType and e.status = :pendingStatus and e.createdAt < :before
            """)
    int failExpiredPendingEmailsByTemplateType(
            @Param("templateType") EmailTemplateType templateType,
            @Param("pendingStatus") EmailDeliveryStatus pendingStatus,
            @Param("failedStatus") EmailDeliveryStatus failedStatus,
            @Param("before") LocalDateTime before,
            @Param("message") String message
    );

    @Modifying
    @Query("update EmailLog e set e.body = :body where e.templateType = :templateType")
    void redactBodiesByTemplateType(
            @Param("templateType") EmailTemplateType templateType,
            @Param("body") String body
    );
}
