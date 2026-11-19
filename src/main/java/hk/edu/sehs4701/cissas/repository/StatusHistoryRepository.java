package hk.edu.sehs4701.cissas.repository;

import hk.edu.sehs4701.cissas.entity.StatusHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface StatusHistoryRepository extends JpaRepository<StatusHistory, Long> {
    List<StatusHistory> findAllByApplication_IdOrderByChangedAtAsc(Long applicationId);
}
