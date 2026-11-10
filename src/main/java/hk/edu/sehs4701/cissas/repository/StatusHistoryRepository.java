package hk.edu.sehs4701.cissas.repository;

import hk.edu.sehs4701.cissas.entity.StatusHistory;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StatusHistoryRepository extends JpaRepository<StatusHistory, Long> {
}
