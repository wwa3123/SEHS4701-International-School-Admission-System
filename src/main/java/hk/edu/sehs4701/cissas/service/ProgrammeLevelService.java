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

import java.time.LocalDate;
import java.util.List;

@Service
public class ProgrammeLevelService {
    public record Response(Long id, Long academicYearId, LevelCode levelCode, String programmeName,
                           Integer seatQuota, LocalDate applicationStartDate, LocalDate applicationEndDate) {
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
    public Response create(Long yearId, LevelCode code, String name, int quota,
                           LocalDate applicationStartDate, LocalDate applicationEndDate) {
        lock.acquire();
        AcademicYear year = years.findById(yearId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Academic year not found"));
        validateDates(applicationStartDate, applicationEndDate);
        if (levels.existsByLevelCodeAndAcademicYear_Id(code, yearId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Programme level already exists for academic year");
        }
        ProgrammeLevel level = new ProgrammeLevel();
        level.setAcademicYear(year);
        level.setLevelCode(code);
        return save(level, name, quota, applicationStartDate, applicationEndDate);
    }

    @Transactional
    public Response update(Long yearId, Long levelId, String name, int quota,
                           LocalDate applicationStartDate, LocalDate applicationEndDate) {
        lock.acquire();
        ProgrammeLevel level = levels.findByIdAndAcademicYear_Id(levelId, yearId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Programme level not found"));
        validateDates(applicationStartDate, applicationEndDate);
        return save(level, name, quota, applicationStartDate, applicationEndDate);
    }

    private void validateDates(LocalDate startDate, LocalDate endDate) {
        if (startDate == null || endDate == null || !startDate.isBefore(endDate)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid programme application period");
        }
    }

    private Response save(ProgrammeLevel level, String name, int quota,
                          LocalDate applicationStartDate, LocalDate applicationEndDate) {
        level.setProgrammeName(name.trim());
        level.setSeatQuota(quota);
        level.setApplicationStartDate(applicationStartDate);
        level.setApplicationEndDate(applicationEndDate);
        level = levels.save(level);
        return response(level, level.getAcademicYear().getId());
    }

    private Response response(ProgrammeLevel level, Long yearId) {
        return new Response(level.getId(), yearId, level.getLevelCode(), level.getProgrammeName(),
                level.getSeatQuota(), level.getApplicationStartDate(), level.getApplicationEndDate());
    }
}
