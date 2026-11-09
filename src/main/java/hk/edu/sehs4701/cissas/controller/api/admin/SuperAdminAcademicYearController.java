package hk.edu.sehs4701.cissas.controller.api.admin;

import hk.edu.sehs4701.cissas.dto.request.AcademicYearLifecycleRequest;
import hk.edu.sehs4701.cissas.dto.request.CreateAcademicYearRequest;
import hk.edu.sehs4701.cissas.dto.request.ProgrammeLevelRequest;
import hk.edu.sehs4701.cissas.dto.request.ProgrammeLevelUpdateRequest;
import hk.edu.sehs4701.cissas.dto.response.AcademicYearResponse;
import hk.edu.sehs4701.cissas.dto.response.ProgrammeLevelResponse;
import hk.edu.sehs4701.cissas.service.AcademicYearService;
import hk.edu.sehs4701.cissas.service.AdminAccountService;
import hk.edu.sehs4701.cissas.service.ProgrammeLevelService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

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

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public AcademicYearResponse create(
            @Valid @RequestBody CreateAcademicYearRequest request, HttpServletRequest servletRequest) {
        adminAccountService.requireSuperAdminForUpdate(servletRequest);
        return academicYearService.create(request.name());
    }

    @PutMapping("/{id}/status")
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public AcademicYearResponse updateStatus(
            @PathVariable Long id,
            @Valid @RequestBody AcademicYearLifecycleRequest request,
            HttpServletRequest servletRequest) {
        adminAccountService.requireSuperAdminForUpdate(servletRequest);
        return academicYearService.updateLifecycle(id, request.status());
    }

    @PostMapping("/{id}/programme-levels")
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public ProgrammeLevelResponse createProgramme(
            @PathVariable Long id, @Valid @RequestBody ProgrammeLevelRequest request, HttpServletRequest servletRequest) {
        adminAccountService.requireSuperAdminForUpdate(servletRequest);
        return programmeLevelService.create(id, request.levelCode(), request.programmeName(),
                request.seatQuota(), request.applicationStartDate(), request.applicationEndDate());
    }

    @GetMapping("/{id}/programme-levels")
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public ResponseEntity<List<ProgrammeLevelResponse>> listProgrammes(
            @PathVariable Long id, HttpServletRequest request) {
        adminAccountService.requireSuperAdminForUpdate(request);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(programmeLevelService.list(id));
    }

    @PutMapping("/{yearId}/programme-levels/{levelId}")
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public ProgrammeLevelResponse updateProgramme(
            @PathVariable Long yearId, @PathVariable Long levelId,
            @Valid @RequestBody ProgrammeLevelUpdateRequest request, HttpServletRequest servletRequest) {
        adminAccountService.requireSuperAdminForUpdate(servletRequest);
        return programmeLevelService.update(yearId, levelId, request.programmeName(),
                request.seatQuota(), request.applicationStartDate(), request.applicationEndDate());
    }
}
