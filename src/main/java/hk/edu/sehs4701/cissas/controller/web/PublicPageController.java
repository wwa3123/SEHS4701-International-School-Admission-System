package hk.edu.sehs4701.cissas.controller.web;

import hk.edu.sehs4701.cissas.service.AcademicYearService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class PublicPageController {

    private final AcademicYearService academicYearService;

    public PublicPageController(AcademicYearService academicYearService) {
        this.academicYearService = academicYearService;
    }

    @GetMapping("/about")
    public String about() {
        return "about";
    }

    @GetMapping("/facilities")
    public String facilities() {
        return "facilities";
    }

    @GetMapping("/programmes")
    public String programmes() {
        return "programmes";
    }

    @GetMapping("/courses")
    public String courses() {
        return "courses";
    }

    @GetMapping("/admission-requirements")
    public String admissionRequirements() {
        return "admission-requirements";
    }

    @GetMapping("/admission-info")
    public String admissionInfo(Model model) {
        model.addAttribute("openYear", academicYearService.getCurrentOpenForApplicationAcademicYear());
        return "admission-info";
    }
}