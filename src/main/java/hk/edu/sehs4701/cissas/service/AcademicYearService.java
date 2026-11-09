package hk.edu.sehs4701.cissas.service;

import hk.edu.sehs4701.cissas.dto.response.AcademicYearResponse;
import hk.edu.sehs4701.cissas.entity.AcademicYear;
import hk.edu.sehs4701.cissas.enums.AcademicYearStatus;
import hk.edu.sehs4701.cissas.repository.AcademicYearRepository;
import hk.edu.sehs4701.cissas.repository.IdentityReviewLockRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

@Service
public class AcademicYearService {
    private final AcademicYearRepository academicYearRepository;
    private final IdentityReviewLockRepository lock;

    public AcademicYearService(AcademicYearRepository academicYearRepository, IdentityReviewLockRepository lock) {
        this.academicYearRepository = academicYearRepository;
        this.lock = lock;
    }

    @Transactional(readOnly = true)
    public List<AcademicYearResponse> list() {
        return academicYearRepository.findAllByOrderByApplicationStartDateDesc().stream()
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
        if (today.isBefore(year.applicationStartDate()) || today.isAfter(year.applicationEndDate())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No academic year open for applications found");
        }
        return year;
    }

    @Transactional(readOnly = true)
    public AcademicYearResponse getActiveAcademicYear() {
        return academicYearRepository.findFirstByStatusOrderByApplicationStartDateDesc(AcademicYearStatus.ACTIVE)
                .map(this::toResponse)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "No academic year open for applications found"));
    }

    @Transactional
    public AcademicYearResponse create(String name, LocalDate applicationStartDate, LocalDate applicationEndDate) {
        if (!applicationStartDate.isBefore(applicationEndDate)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Application start date must be before application end date");
        }
        String normalizedName = name.trim();
        lock.acquire();
        if (academicYearRepository.existsByName(normalizedName)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Academic year name already exists");
        }

        AcademicYear academicYear = new AcademicYear();
        academicYear.setName(normalizedName);
        academicYear.setApplicationStartDate(applicationStartDate);
        academicYear.setApplicationEndDate(applicationEndDate);
        academicYear.setStatus(AcademicYearStatus.PLANNING);
        return toResponse(academicYearRepository.save(academicYear));
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
        if (lifecycle == AcademicYearStatus.ACTIVE
                && academicYearRepository.existsByStatusAndIdNot(AcademicYearStatus.ACTIVE, id)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Another academic year is active");
        }
        academicYear.setStatus(lifecycle);
        return toResponse(academicYearRepository.save(academicYear));
    }

    private AcademicYearResponse toResponse(AcademicYear academicYear) {
        return new AcademicYearResponse(
                academicYear.getId(),
                academicYear.getName(),
                academicYear.getApplicationStartDate(),
                academicYear.getApplicationEndDate(),
                academicYear.getStatus()
        );
    }
}
