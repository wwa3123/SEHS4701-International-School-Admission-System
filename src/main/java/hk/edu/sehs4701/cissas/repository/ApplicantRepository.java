package hk.edu.sehs4701.cissas.repository;

import hk.edu.sehs4701.cissas.entity.Applicant;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.EntityGraph;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface ApplicantRepository extends JpaRepository<Applicant, Long> {
    @EntityGraph(attributePaths = "identityDocuments")
    List<Applicant> findAllByDateOfBirth(LocalDate dateOfBirth);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from Applicant a join a.identityDocuments d where d.documentNumberHash = :documentNumberHash")
    Optional<Applicant> findByIdentityDocumentHashForUpdate(@Param("documentNumberHash") String documentNumberHash);
}
