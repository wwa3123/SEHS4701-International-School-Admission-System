package hk.edu.sehs4701.cissas.controller.web;

import hk.edu.sehs4701.cissas.dto.request.*;
import hk.edu.sehs4701.cissas.enums.AcademicYearStatus;
import hk.edu.sehs4701.cissas.enums.LevelCode;
import hk.edu.sehs4701.cissas.exception.SeatQuotaExceededException;
import hk.edu.sehs4701.cissas.service.AcademicYearService;
import hk.edu.sehs4701.cissas.service.AdminAccountService;
import hk.edu.sehs4701.cissas.service.ProgrammeLevelService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Academic-year and programme DTO forms replace /api/super-admin/academic-years.
 * POST actions retain service locking, lifecycle and quota-expiry audit semantics.
 */
@Controller
@RequestMapping("/super-admin/academic-years")
@Transactional(isolation = Isolation.READ_COMMITTED)
public class SuperAdminAcademicYearPageController {
    private final AcademicYearService years;
    private final AdminAccountService accounts;
    private final ProgrammeLevelService levels;

    public SuperAdminAcademicYearPageController(AcademicYearService years, AdminAccountService accounts,
                                                ProgrammeLevelService levels) {
        this.years = years;
        this.accounts = accounts;
        this.levels = levels;
    }

    @GetMapping
    public String list(HttpServletRequest request, HttpServletResponse response, Model model) {
        guard(request, response);
        model.addAttribute("years", years.list());
        return "super-admin/academic-years";
    }

    @GetMapping("/new")
    public String createForm(HttpServletRequest request, HttpServletResponse response, Model model) {
        guard(request, response);
        model.addAttribute("yearForm", new CreateAcademicYearRequest(""));
        return "super-admin/academic-year-form";
    }

    @PostMapping
    public String create(@Valid @ModelAttribute("yearForm") CreateAcademicYearRequest form, BindingResult errors,
                         HttpServletRequest request, HttpServletResponse response, Model model,
                         RedirectAttributes redirect) {
        guard(request, response);
        if (invalid(errors, request, response, model, "super-admin/academic-year-form")) {
            return "super-admin/academic-year-form";
        }
        var year = years.create(form.name());
        redirect.addFlashAttribute("message", "Academic year created.");
        return "redirect:/super-admin/academic-years/" + year.id();
    }

    @GetMapping({"/{id}", "/{id}/programme-levels"})
    public String detail(@PathVariable Long id, HttpServletRequest request, HttpServletResponse response, Model model) {
        guard(request, response);
        detailModel(id, model);
        model.addAttribute("lifecycleForm", new AcademicYearLifecycleRequest(years.get(id).status()));
        return "super-admin/academic-year";
    }

    @PostMapping("/{id}/status")
    public String updateStatus(@PathVariable Long id,
                               @Valid @ModelAttribute("lifecycleForm") AcademicYearLifecycleRequest form,
                               BindingResult errors, HttpServletRequest request, HttpServletResponse response,
                               Model model, RedirectAttributes redirect) {
        guard(request, response);
        detailModel(id, model);
        if (invalid(errors, request, response, model, "super-admin/academic-year")) {
            return "super-admin/academic-year";
        }
        years.updateLifecycle(id, form.status());
        redirect.addFlashAttribute("message", "Academic year status updated.");
        return "redirect:/super-admin/academic-years/" + id;
    }

    @GetMapping("/{id}/programme-levels/new")
    public String programmeForm(@PathVariable Long id, HttpServletRequest request,
                                HttpServletResponse response, Model model) {
        guard(request, response);
        programmeModel(id, null, model);
        model.addAttribute("programmeForm", new ProgrammeLevelRequest(null, "", 0, null, null));
        return "super-admin/programme-form";
    }

    @PostMapping("/{id}/programme-levels")
    public String createProgramme(@PathVariable Long id,
                                  @Valid @ModelAttribute("programmeForm") ProgrammeLevelRequest form,
                                  BindingResult errors, HttpServletRequest request, HttpServletResponse response,
                                  Model model, RedirectAttributes redirect) {
        guard(request, response);
        programmeModel(id, null, model);
        if (invalid(errors, request, response, model, "super-admin/programme-form")) {
            return "super-admin/programme-form";
        }
        levels.create(id, form.levelCode(), form.programmeName(), form.seatQuota(),
                form.applicationStartDate(), form.applicationEndDate());
        redirect.addFlashAttribute("message", "Programme level created.");
        return "redirect:/super-admin/academic-years/" + id;
    }

    @GetMapping("/{yearId}/programme-levels/{levelId}/edit")
    public String editProgramme(@PathVariable Long yearId, @PathVariable Long levelId,
                                HttpServletRequest request, HttpServletResponse response, Model model) {
        guard(request, response);
        programmeModel(yearId, levelId, model);
        var level = levels.list(yearId).stream().filter(item -> item.id().equals(levelId)).findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Programme level not found"));
        model.addAttribute("programmeForm", new ProgrammeLevelUpdateRequest(level.programmeName(), level.seatQuota(),
                level.applicationStartDate(), level.applicationEndDate()));
        return "super-admin/programme-form";
    }

    @PostMapping("/{yearId}/programme-levels/{levelId}")
    @Transactional(isolation = Isolation.READ_COMMITTED, noRollbackFor = SeatQuotaExceededException.class)
    public String updateProgramme(@PathVariable Long yearId, @PathVariable Long levelId,
                                  @Valid @ModelAttribute("programmeForm") ProgrammeLevelUpdateRequest form,
                                  BindingResult errors, HttpServletRequest request, HttpServletResponse response,
                                  Model model, RedirectAttributes redirect) {
        guard(request, response);
        programmeModel(yearId, levelId, model);
        if (invalid(errors, request, response, model, "super-admin/programme-form")) {
            return "super-admin/programme-form";
        }
        levels.update(yearId, levelId, form.programmeName(), form.seatQuota(),
                form.applicationStartDate(), form.applicationEndDate());
        redirect.addFlashAttribute("message", "Programme level updated.");
        return "redirect:/super-admin/academic-years/" + yearId;
    }

    private void guard(HttpServletRequest request, HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-store");
        accounts.requireSuperAdminForUpdate(request);
    }

    private void detailModel(Long id, Model model) {
        model.addAttribute("year", years.get(id));
        model.addAttribute("levels", levels.list(id));
        model.addAttribute("statuses", AcademicYearStatus.values());
    }

    private void programmeModel(Long yearId, Long levelId, Model model) {
        model.addAttribute("year", years.get(yearId));
        model.addAttribute("levelId", levelId);
        model.addAttribute("levelCodes", LevelCode.values());
    }

    private boolean invalid(BindingResult errors, HttpServletRequest request, HttpServletResponse response,
                            Model model, String view) {
        request.setAttribute("yearErrorView", view);
        request.setAttribute("yearErrorModel", new LinkedHashMap<>(model.asMap()));
        if (!errors.hasErrors()) return false;
        response.setStatus(400);
        return true;
    }

    // Keep service rollbacks (and quota-refusal audit commits) outside the error-page rendering.
    @ExceptionHandler(ResponseStatusException.class)
    public ModelAndView invalid(ResponseStatusException exception, HttpServletRequest request,
                                HttpServletResponse response) {
        if ((exception.getStatusCode().value() != 400 && exception.getStatusCode().value() != 409)
                || !(request.getAttribute("yearErrorView") instanceof String view)) throw exception;
        response.setStatus(exception.getStatusCode().value());
        ModelAndView page = new ModelAndView(view);
        if (request.getAttribute("yearErrorModel") instanceof Map<?, ?> attributes) {
            attributes.forEach((name, value) -> page.addObject(name.toString(), value));
        }
        page.addObject("error", exception.getReason());
        return page;
    }
}
