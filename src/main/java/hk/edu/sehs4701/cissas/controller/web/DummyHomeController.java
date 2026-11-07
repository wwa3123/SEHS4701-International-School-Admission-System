package hk.edu.sehs4701.cissas.controller.web;

import hk.edu.sehs4701.cissas.service.AcademicYearService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.ui.Model;

@Controller
public class DummyHomeController {
    private final AcademicYearService academicYearService;

    @Autowired
    public DummyHomeController(AcademicYearService academicYearService) {
        this.academicYearService = academicYearService;
    }

    @GetMapping("/")
    public String index(Model model) {
        model.addAttribute("appName", "My Spring Boot App");
        model.addAttribute("username", "Developer");
        model.addAttribute("academicYears", academicYearService.list());
        return "index";
    }
}
