package hk.edu.sehs4701.cissas.repository;

import hk.edu.sehs4701.cissas.entity.OneTimeCode;
import hk.edu.sehs4701.cissas.entity.ProgrammeLevel;
import hk.edu.sehs4701.cissas.enums.LevelCode;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ProgrammeLevelRepository extends JpaRepository<ProgrammeLevel, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<ProgrammeLevel> findByLevelCode(LevelCode attr0);
}
