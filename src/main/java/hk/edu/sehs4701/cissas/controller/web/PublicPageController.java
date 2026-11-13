package hk.edu.sehs4701.cissas.controller.web;

import hk.edu.sehs4701.cissas.service.AcademicYearService;
import hk.edu.sehs4701.cissas.service.ProgrammeLevelService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.server.ResponseStatusException;

@Controller
public class PublicPageController {

    private final AcademicYearService academicYearService;
    private final ProgrammeLevelService programmeLevelService;

    public PublicPageController(AcademicYearService academicYearService, ProgrammeLevelService programmeLevelService) {
        this.academicYearService = academicYearService;
        this.programmeLevelService = programmeLevelService;
    }

    @GetMapping("/about")
    public String about() {
        return "about";
    }

    @GetMapping("/admission-info")
    public String admissionInfo(Model model) {
        try {
            var year = academicYearService.getCurrentOpenForApplicationAcademicYear();
            model.addAttribute("openYear", year);
            model.addAttribute("levels", programmeLevelService.list(year.id()));
        } catch (ResponseStatusException exception) {
            if (exception.getStatusCode() != HttpStatus.NOT_FOUND) throw exception;
            model.addAttribute("openYear", null);
            model.addAttribute("levels", java.util.List.of());
        }
        return "admission-info";
    }
}