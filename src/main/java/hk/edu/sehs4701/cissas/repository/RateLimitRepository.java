package hk.edu.sehs4701.cissas.repository;

import hk.edu.sehs4701.cissas.entity.RateLimit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

public interface RateLimitRepository extends JpaRepository<RateLimit, String> {
    @Modifying
    @Transactional
    @Query(value = """
            INSERT INTO registration_rate_limit (rate_key, window_started_at, request_count)
            VALUES (:rateKey, :now, 1)
            ON DUPLICATE KEY UPDATE
                request_count = IF(window_started_at <= :cutoff, 1, request_count + 1),
                window_started_at = IF(window_started_at <= :cutoff, :now, window_started_at)
            """, nativeQuery = true)
    int incrementWindowCount(
            @Param("rateKey") String rateKey,
            @Param("now") LocalDateTime now,
            @Param("cutoff") LocalDateTime cutoff
    );

    @Query("select r.requestCount from RateLimit r where r.rateKey = :rateKey")
    int findRequestCount(@Param("rateKey") String rateKey);

    @Modifying
    @Transactional
    @Query("delete from RateLimit r where r.windowStartedAt < :cutoff")
    int deleteExpired(@Param("cutoff") LocalDateTime cutoff);
}
