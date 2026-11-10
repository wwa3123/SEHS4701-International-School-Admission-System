package hk.edu.sehs4701.cissas.entity;

import hk.edu.sehs4701.cissas.enums.AcademicYearStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(
        name = "academic_year",
        uniqueConstraints = @UniqueConstraint(name = "uk_academic_year_name", columnNames = "name")
)
public class AcademicYear {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 20)
    private String name;

    @Column(name = "creation_date", nullable = false)
    private LocalDate creationDate;

    @Column(name = "archive_date")
    private LocalDate archiveDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AcademicYearStatus status;
}
