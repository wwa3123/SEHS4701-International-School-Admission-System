package hk.edu.sehs4701.cissas.repository;

import hk.edu.sehs4701.cissas.entity.AcademicYear;
import hk.edu.sehs4701.cissas.enums.AcademicYearStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AcademicYearRepository extends JpaRepository<AcademicYear, Long> {
    List<AcademicYear> findAllByOrderByCreationDateDesc();
    Optional<AcademicYear> findFirstByStatusOrderByCreationDateDesc(AcademicYearStatus status);
    boolean existsByStatusAndIdNot(AcademicYearStatus status, Long id);
    boolean existsByName(String name);
}
