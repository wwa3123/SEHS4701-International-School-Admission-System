package hk.edu.sehs4701.cissas.entity;

import hk.edu.sehs4701.cissas.enums.AcademicYearStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Check;

import java.time.LocalDate;

@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(
        name = "academic_year",
        uniqueConstraints = @UniqueConstraint(name = "uk_academic_year_name", columnNames = "name")
)
@Check(constraints = "application_start_date < application_end_date")
public class AcademicYear {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 20)
    private String name;

    @Column(name = "application_start_date", nullable = false)
    private LocalDate applicationStartDate;

    @Column(name = "application_end_date", nullable = false)
    private LocalDate applicationEndDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AcademicYearStatus status;
}
