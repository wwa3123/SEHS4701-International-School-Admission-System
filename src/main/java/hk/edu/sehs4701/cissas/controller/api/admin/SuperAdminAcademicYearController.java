package hk.edu.sehs4701.cissas.controller.api.admin;

import hk.edu.sehs4701.cissas.dto.response.AcademicYearResponse;
import hk.edu.sehs4701.cissas.enums.AcademicYearStatus;
import hk.edu.sehs4701.cissas.enums.LevelCode;
import hk.edu.sehs4701.cissas.service.AcademicYearService;
import hk.edu.sehs4701.cissas.service.AdminAccountService;
import hk.edu.sehs4701.cissas.service.ProgrammeLevelService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Min;
import org.springframework.http.HttpStatus;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/super-admin/academic-years")
public class SuperAdminAcademicYearController {
    private final AcademicYearService academicYearService;
    private final AdminAccountService adminAccountService;
    private final ProgrammeLevelService programmeLevelService;

    public SuperAdminAcademicYearController(AcademicYearService academicYearService,
                                             AdminAccountService adminAccountService,
                                             ProgrammeLevelService programmeLevelService) {
        this.academicYearService = academicYearService;
        this.adminAccountService = adminAccountService;
        this.programmeLevelService = programmeLevelService;
    }

    public record CreateRequest(
            @NotBlank @Size(max = 20) String name) {
    }

    public record LifecycleRequest(@NotNull AcademicYearStatus status) {
    }

    public record ProgrammeRequest(@NotNull LevelCode levelCode,
                                   @NotBlank @Size(max = 120) String programmeName,
                                   @NotNull @Min(0) Integer seatQuota,
                                   @NotNull LocalDate applicationStartDate,
                                   @NotNull LocalDate applicationEndDate) {
    }

    public record ProgrammeUpdateRequest(@NotBlank @Size(max = 120) String programmeName,
                                         @NotNull @Min(0) Integer seatQuota,
                                         @NotNull LocalDate applicationStartDate,
                                         @NotNull LocalDate applicationEndDate) {
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public AcademicYearResponse create(@Valid @RequestBody CreateRequest request, HttpServletRequest servletRequest) {
        adminAccountService.requireSuperAdminForUpdate(servletRequest);
        return academicYearService.create(request.name());
    }

    @PutMapping("/{id}/status")
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public AcademicYearResponse updateStatus(
            @PathVariable Long id,
            @Valid @RequestBody LifecycleRequest request,
            HttpServletRequest servletRequest) {
        adminAccountService.requireSuperAdminForUpdate(servletRequest);
        return academicYearService.updateLifecycle(id, request.status());
    }

    @PostMapping("/{id}/programme-levels")
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public ProgrammeLevelService.Response createProgramme(
            @PathVariable Long id, @Valid @RequestBody ProgrammeRequest request, HttpServletRequest servletRequest) {
        adminAccountService.requireSuperAdminForUpdate(servletRequest);
        return programmeLevelService.create(id, request.levelCode(), request.programmeName(),
                request.seatQuota(), request.applicationStartDate(), request.applicationEndDate());
    }

    @GetMapping("/{id}/programme-levels")
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public ResponseEntity<List<ProgrammeLevelService.Response>> listProgrammes(
            @PathVariable Long id, HttpServletRequest request) {
        adminAccountService.requireSuperAdminForUpdate(request);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(programmeLevelService.list(id));
    }

    @PutMapping("/{yearId}/programme-levels/{levelId}")
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public ProgrammeLevelService.Response updateProgramme(
            @PathVariable Long yearId, @PathVariable Long levelId,
            @Valid @RequestBody ProgrammeUpdateRequest request, HttpServletRequest servletRequest) {
        adminAccountService.requireSuperAdminForUpdate(servletRequest);
        return programmeLevelService.update(yearId, levelId, request.programmeName(),
                request.seatQuota(), request.applicationStartDate(), request.applicationEndDate());
    }
}
