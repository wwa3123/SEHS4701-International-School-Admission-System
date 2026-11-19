package hk.edu.sehs4701.cissas.repository;

import hk.edu.sehs4701.cissas.entity.IdentityDocument;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import java.util.Optional;
import java.util.List;

public interface IdentityDocumentRepository extends JpaRepository<IdentityDocument, Long> {
    boolean existsByDocumentNumberHash(String documentNumberHash);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<IdentityDocument> findByDocumentNumberHash(String documentNumberHash);

    List<IdentityDocument> findAllByMember_IdOrderByIdAsc(Long memberId);
}
