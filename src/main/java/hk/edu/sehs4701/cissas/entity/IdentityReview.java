package hk.edu.sehs4701.cissas.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "identity_review", uniqueConstraints = @UniqueConstraint(
        name = "uk_identity_review_current", columnNames = {"first_applicant_id", "second_applicant_id", "current_marker"}))
public class IdentityReview {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "first_applicant_id", nullable = false, updatable = false)
    private Applicant firstApplicant;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "second_applicant_id", nullable = false, updatable = false)
    private Applicant secondApplicant;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "first_application_id", nullable = false, updatable = false)
    private Application firstApplication;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "second_application_id", nullable = false, updatable = false)
    private Application secondApplication;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "reviewed_by", nullable = false, updatable = false)
    private Admin reviewedBy;

    @Column(name = "same_person", nullable = false, updatable = false)
    private boolean samePerson;

    @Column(name = "current_marker")
    private Integer currentMarker = 1;

    @Column(name = "reviewed_at", nullable = false, updatable = false)
    private LocalDateTime reviewedAt = LocalDateTime.now();

    public IdentityReview(Application first, Application second, Admin admin, boolean samePerson) {
        this.firstApplication = first;
        this.secondApplication = second;
        boolean firstApplicantComesFirst = first.getApplicant().getId() < second.getApplicant().getId();
        this.firstApplicant = firstApplicantComesFirst ? first.getApplicant() : second.getApplicant();
        this.secondApplicant = firstApplicantComesFirst ? second.getApplicant() : first.getApplicant();
        this.reviewedBy = admin;
        this.samePerson = samePerson;
    }

    public void supersede() {
        currentMarker = null;
    }
}
