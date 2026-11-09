package hk.edu.sehs4701.cissas.service;

import hk.edu.sehs4701.cissas.entity.AcademicYear;
import hk.edu.sehs4701.cissas.entity.ProgrammeLevel;
import hk.edu.sehs4701.cissas.enums.LevelCode;
import hk.edu.sehs4701.cissas.repository.AcademicYearRepository;
import hk.edu.sehs4701.cissas.repository.IdentityReviewLockRepository;
import hk.edu.sehs4701.cissas.repository.ProgrammeLevelRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class ProgrammeLevelService {
    public record Response(Long id, Long academicYearId, LevelCode levelCode, String programmeName,
                           Integer seatQuota, LocalDateTime applicationDeadline) {
    }

    @Transactional(readOnly = true)
    public List<Response> list(Long yearId) {
        if (!years.existsById(yearId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Academic year not found");
        }
        return levels.findAllByAcademicYear_IdOrderByLevelCodeAsc(yearId).stream()
                .map(level -> response(level, yearId)).toList();
    }

    private final ProgrammeLevelRepository levels;
    private final AcademicYearRepository years;
    private final IdentityReviewLockRepository lock;

    public ProgrammeLevelService(ProgrammeLevelRepository levels, AcademicYearRepository years,
                                 IdentityReviewLockRepository lock) {
        this.levels = levels;
        this.years = years;
        this.lock = lock;
    }

    @Transactional
    public Response create(Long yearId, LevelCode code, String name, int quota, LocalDateTime deadline) {
        lock.acquire();
        AcademicYear year = years.findById(yearId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Academic year not found"));
        validateDeadline(year, deadline);
        if (levels.existsByLevelCodeAndAcademicYear_Id(code, yearId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Programme level already exists for academic year");
        }
        ProgrammeLevel level = new ProgrammeLevel();
        level.setAcademicYear(year);
        level.setLevelCode(code);
        return save(level, name, quota, deadline);
    }

    @Transactional
    public Response update(Long yearId, Long levelId, String name, int quota, LocalDateTime deadline) {
        lock.acquire();
        ProgrammeLevel level = levels.findByIdAndAcademicYear_Id(levelId, yearId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Programme level not found"));
        validateDeadline(level.getAcademicYear(), deadline);
        return save(level, name, quota, deadline);
    }

    private void validateDeadline(AcademicYear year, LocalDateTime deadline) {
        if (deadline.toLocalDate().isBefore(year.getApplicationStartDate())
                || deadline.toLocalDate().isAfter(year.getApplicationEndDate())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Programme deadline must be within the academic year application period");
        }
    }

    private Response save(ProgrammeLevel level, String name, int quota, LocalDateTime deadline) {
        level.setProgrammeName(name.trim());
        level.setSeatQuota(quota);
        level.setApplicationDeadline(deadline);
        level = levels.save(level);
        return response(level, level.getAcademicYear().getId());
    }

    private Response response(ProgrammeLevel level, Long yearId) {
        return new Response(level.getId(), yearId, level.getLevelCode(), level.getProgrammeName(),
                level.getSeatQuota(), level.getApplicationDeadline());
    }
}
