package hk.edu.cis.cissas.entity;

import hk.edu.cis.cissas.enums.LevelCode;
import jakarta.persistence.*;
import jakarta.validation.constraints.Min;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(
        name = "programme_level",
        uniqueConstraints = @UniqueConstraint(name = "uk_programme_level_code", columnNames = "level_code")
)
public class ProgrammeLevel {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "level_code", nullable = false, length = 10)
    private LevelCode levelCode;

    @Column(name = "programme_name", nullable = false, length = 120)
    private String programmeName;

    @Min(0)
    @Column(name = "seat_quota", nullable = false)
    private Integer seatQuota = 150;

    @Column(name = "application_deadline", nullable = false)
    private LocalDateTime applicationDeadline;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();
}
