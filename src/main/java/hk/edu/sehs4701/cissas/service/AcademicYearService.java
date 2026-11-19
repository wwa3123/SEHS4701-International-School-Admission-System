package hk.edu.sehs4701.cissas.service;

import hk.edu.sehs4701.cissas.dto.response.AcademicYearResponse;
import hk.edu.sehs4701.cissas.entity.AcademicYear;
import hk.edu.sehs4701.cissas.entity.ProgrammeLevel;
import hk.edu.sehs4701.cissas.enums.AcademicYearStatus;
import hk.edu.sehs4701.cissas.enums.ApplicationStatus;
import hk.edu.sehs4701.cissas.enums.LevelCode;
import hk.edu.sehs4701.cissas.repository.AcademicYearRepository;
import hk.edu.sehs4701.cissas.repository.ApplicationRepository;
import hk.edu.sehs4701.cissas.repository.IdentityReviewLockRepository;
import hk.edu.sehs4701.cissas.repository.ProgrammeLevelRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.List;

@Service
public class AcademicYearService {
    private final AcademicYearRepository academicYearRepository;
    private final ApplicationRepository applications;
    private final IdentityReviewLockRepository lock;
    private final ProgrammeLevelRepository programmeLevels;

    @Autowired
    public AcademicYearService(AcademicYearRepository academicYearRepository, ApplicationRepository applications,
                               IdentityReviewLockRepository lock,
                               ProgrammeLevelRepository programmeLevels) {
        this.academicYearRepository = academicYearRepository;
        this.applications = applications;
        this.lock = lock;
        this.programmeLevels = programmeLevels;
    }

    @Transactional(readOnly = true)
    public List<AcademicYearResponse> list() {
        return academicYearRepository.findAllByOrderByCreationDateDesc().stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public AcademicYearResponse get(Long id) {
        return academicYearRepository.findById(id)
                .map(this::toResponse)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Academic year not found"));
    }

    @Transactional(readOnly = true)
    public AcademicYearResponse getCurrentOpenForApplicationAcademicYear() {
        AcademicYearResponse year = getActiveAcademicYear();
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Hong_Kong"));
        if (!programmeLevels.existsOpenForApplication(year.id(), today, LevelCode.S6)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No academic year open for applications found");
        }
        return year;
    }

    @Transactional(readOnly = true)
    public AcademicYearResponse getActiveAcademicYear() {
        return academicYearRepository.findFirstByStatusOrderByCreationDateDesc(AcademicYearStatus.ACTIVE)
                .map(this::toResponse)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "No academic year open for applications found"));
    }

    @Transactional
    public AcademicYearResponse create(String name) {
        String normalizedName = name.trim();
        lock.acquire();
        if (academicYearRepository.existsByName(normalizedName)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Academic year name already exists");
        }

        AcademicYear academicYear = new AcademicYear();
        academicYear.setName(normalizedName);
        academicYear.setCreationDate(LocalDate.now(ZoneId.of("Asia/Hong_Kong")));
        academicYear.setStatus(AcademicYearStatus.PLANNING);
        AcademicYear savedYear = academicYearRepository.save(academicYear);
        programmeLevels.saveAll(Arrays.stream(LevelCode.values()).map(code -> {
            ProgrammeLevel level = new ProgrammeLevel();
            level.setAcademicYear(savedYear);
            level.setLevelCode(code);
            level.setProgrammeName(code == LevelCode.S5 || code == LevelCode.S6 ? "IB DP" : "IB MYP");
            level.setSeatQuota(150);
            level.setAdmissionOpen(code != LevelCode.S6);
            return level;
        }).toList());
        return toResponse(savedYear);
    }

    @Transactional
    public AcademicYearResponse updateLifecycle(Long id, AcademicYearStatus lifecycle) {
        if (lifecycle == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid academic year lifecycle status");
        }
        // Serialize lifecycle changes with admissions submissions using the existing database lock.
        lock.acquire();
        AcademicYear academicYear = academicYearRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Academic year not found"));
        if (academicYear.getStatus() == AcademicYearStatus.ARCHIVED && lifecycle != AcademicYearStatus.ARCHIVED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Archived academic years are read-only");
        }
        if (lifecycle == AcademicYearStatus.ACTIVE
                && academicYearRepository.existsByStatusAndIdNot(AcademicYearStatus.ACTIVE, id)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Another academic year is active");
        }
        if (lifecycle == AcademicYearStatus.ACTIVE) {
            List<ProgrammeLevel> levels = programmeLevels.findAllByAcademicYear_IdOrderByLevelCodeAsc(id);
            if (levels.size() != LevelCode.values().length || levels.stream()
                    .anyMatch(level -> level.getApplicationStartDate() == null || level.getApplicationEndDate() == null)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "Set an opening and closing date for every programme level before activation");
            }
        }
        if (lifecycle == AcademicYearStatus.ARCHIVED
                && applications.countByAcademicYear_IdAndStatus(id, ApplicationStatus.Pending) > 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Academic year cannot be archived while applications are pending");
        }
        if (lifecycle == AcademicYearStatus.ARCHIVED
                && applications.countByAcademicYear_IdAndStatus(id, ApplicationStatus.Success) > 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Academic year cannot be archived while offers are awaiting a response");
        }
        if (lifecycle == AcademicYearStatus.ARCHIVED
                && (academicYear.getStatus() != AcademicYearStatus.ARCHIVED || academicYear.getArchiveDate() == null)) {
            academicYear.setArchiveDate(LocalDate.now(ZoneId.of("Asia/Hong_Kong")));
        } else if (lifecycle != AcademicYearStatus.ARCHIVED) {
            academicYear.setArchiveDate(null);
        }
        academicYear.setStatus(lifecycle);
        return toResponse(academicYearRepository.save(academicYear));
    }

    private AcademicYearResponse toResponse(AcademicYear academicYear) {
        return new AcademicYearResponse(
                academicYear.getId(),
                academicYear.getName(),
                academicYear.getCreationDate(),
                academicYear.getArchiveDate(),
                academicYear.getStatus()
        );
    }
}
