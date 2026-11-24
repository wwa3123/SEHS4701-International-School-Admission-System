package hk.edu.sehs4701.cissas.controller.web;

import hk.edu.sehs4701.cissas.dto.response.AcademicYearResponse;
import hk.edu.sehs4701.cissas.service.AcademicYearService;
import hk.edu.sehs4701.cissas.service.ProgrammeLevelService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

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

    @GetMapping("/")
    public String home(Model model) {
        addAdmissionInfo(model);
        return "index";
    }

    @GetMapping("/admission-info")
    public String admissionInfo(Model model) {
        addAdmissionInfo(model);
        return "admission-info";
    }

    private void addAdmissionInfo(Model model) {
        model.addAttribute("today", LocalDate.now(ZoneId.of("Asia/Hong_Kong")));
        AcademicYearResponse year;
        try {
            year = academicYearService.getCurrentOpenForApplicationAcademicYear();
        } catch (ResponseStatusException exception) {
            if (exception.getStatusCode() != HttpStatus.NOT_FOUND) throw exception;
            model.addAttribute("openYear", null);
            model.addAttribute("levels", List.of());
            return;
        }
        model.addAttribute("openYear", year);
        model.addAttribute("levels", programmeLevelService.list(year.id()));
    }

    @GetMapping("/programmes")
    public String programmes() {
        return "programmes";
    }

    @GetMapping("/course")
    public String course() {
        return "course";
    }

    @GetMapping("/facilities")
    public String facilities() {
        return "facilities";
    }

    @GetMapping("/admission-requirements")
    public String admissionRequirements() {
        return "admission-requirements";
    }
}
