package hk.edu.sehs4701.cissas.controller.web;

import hk.edu.sehs4701.cissas.service.AcademicYearService;
import hk.edu.sehs4701.cissas.service.ProgrammeLevelService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

/** Public DTO discovery pages replace /api/public/academic-years. */
@Controller
@RequestMapping("/academic-years")
public class AcademicYearPageController {
    private final AcademicYearService years;
    private final ProgrammeLevelService levels;

    public AcademicYearPageController(AcademicYearService years, ProgrammeLevelService levels) {
        this.years = years;
        this.levels = levels;
    }

    @GetMapping
    public String list(Model model) {
        model.addAttribute("years", years.list());
        return "public/academic-years";
    }

    @GetMapping("/open-for-application")
    public String open(Model model) {
        try {
            return detail(years.getCurrentOpenForApplicationAcademicYear().id(), model);
        } catch (ResponseStatusException exception) {
            if (exception.getStatusCode().value() != 404) throw exception;
            model.addAttribute("error", exception.getReason());
            return list(model);
        }
    }

    @GetMapping({"/{id}", "/{id}/programme-levels"})
    public String detail(@PathVariable Long id, Model model) {
        model.addAttribute("year", years.get(id));
        model.addAttribute("levels", levels.list(id));
        return "public/academic-year";
    }
}
