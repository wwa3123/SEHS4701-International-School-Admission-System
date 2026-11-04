package hk.edu.sehs4701.cissas.repository;

import hk.edu.sehs4701.cissas.entity.IdentityDocument;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IdentityDocumentRepository extends JpaRepository<IdentityDocument, Long> {
    boolean existsByDocumentNumberHash(String documentNumberHash);
}
