package hk.edu.sehs4701.cissas.repository;

import hk.edu.sehs4701.cissas.entity.AcademicYear;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AcademicYearRepository extends JpaRepository<AcademicYear, Long> {
    List<AcademicYear> findAllByOrderByStartDateDesc();
}
