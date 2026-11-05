package hk.edu.sehs4701.cissas.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "registration_rate_limit", indexes = {
        @Index(name = "idx_registration_rate_limit_window", columnList = "window_started_at")
})
@Getter
@Setter
@NoArgsConstructor
public class RateLimit {
    @Id
    @Column(name = "rate_key", length = 64)
    private String rateKey;

    @Column(name = "window_started_at", nullable = false)
    private LocalDateTime windowStartedAt;

    @Column(name = "request_count", nullable = false)
    private int requestCount;
}
