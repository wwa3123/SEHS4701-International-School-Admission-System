package hk.edu.sehs4701.cissas.controller.api.pub;

import hk.edu.sehs4701.cissas.dto.response.AcademicYearResponse;
import hk.edu.sehs4701.cissas.service.AcademicYearService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/public/academic-years")
public class AcademicYearController {
    private final AcademicYearService academicYearService;

    public AcademicYearController(AcademicYearService academicYearService) {
        this.academicYearService = academicYearService;
    }

    @GetMapping
    public List<AcademicYearResponse> list() {
        return academicYearService.list();
    }

    @GetMapping("/open-for-application")
    public AcademicYearResponse getCurrentOpenForApplicationAcademicYear() {
        return academicYearService.getCurrentOpenForApplicationAcademicYear();
    }

    @GetMapping("/{id}")
    public AcademicYearResponse get(@PathVariable Long id) {
        return academicYearService.get(id);
    }
}
