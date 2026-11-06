package hk.edu.sehs4701.cissas.entity;

import hk.edu.sehs4701.cissas.enums.ApplicationStatus;
import io.hypersistence.utils.hibernate.id.Tsid;
import jakarta.persistence.*;
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
        name = "application",
        uniqueConstraints = @UniqueConstraint(name = "uk_application_number", columnNames = "application_number")
)
public class Application {
    @Id
    @Tsid
    @Column(name = "application_id")
    private Long id;

    @Column(name = "application_number", nullable = false, length = 30)
    private String applicationNumber;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "applicant_id", nullable = false)
    private Applicant applicant;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "programme_level_id", nullable = false)
    private ProgrammeLevel programmeLevel;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ApplicationStatus status = ApplicationStatus.Pending;

    @Column(name = "manual_identity_review_required", nullable = false)
    private boolean manualIdentityReviewRequired;

    @Column(name = "submitted_at", nullable = false, updatable = false)
    private LocalDateTime submittedAt = LocalDateTime.now();

    @Column(name = "result_released_at")
    private LocalDateTime resultReleasedAt;

    @Column(name = "acceptance_deadline")
    private LocalDateTime acceptanceDeadline;

    @Column(name = "responded_at")
    private LocalDateTime respondedAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();


}