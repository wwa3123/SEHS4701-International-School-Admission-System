package hk.edu.sehs4701.cissas.repository;

import hk.edu.sehs4701.cissas.entity.Applicant;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.Optional;

public interface ApplicantRepository extends JpaRepository<Applicant, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from Applicant a join a.identityDocuments d where d.documentNumberHash = :documentNumberHash")
    Optional<Applicant> findByIdentityDocumentHashForUpdate(@Param("documentNumberHash") String documentNumberHash);
}
