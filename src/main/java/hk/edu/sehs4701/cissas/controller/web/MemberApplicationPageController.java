package hk.edu.sehs4701.cissas.controller.web;

import hk.edu.sehs4701.cissas.dto.request.ApplicationSubmissionRequest;
import hk.edu.sehs4701.cissas.dto.request.OfferDecisionRequest;
import hk.edu.sehs4701.cissas.enums.IdentityDocumentType;
import hk.edu.sehs4701.cissas.service.AcademicYearService;
import hk.edu.sehs4701.cissas.service.AdmissionsApplicationService;
import hk.edu.sehs4701.cissas.service.ApplicationResultService;
import hk.edu.sehs4701.cissas.service.MemberSessionService;
import hk.edu.sehs4701.cissas.service.ProgrammeLevelService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

/**
 * SSR routes use business services and session-bound offer confirmations.
 * These routes replace the former JSON /api/member/applications endpoints.
 * Templates receive DTOs: applications (Slice), applicationForm with programmeLevels/documentTypes,
 * result (MemberApplicationResult), or offer (OfferConfirmation).
 * Forms POST to the collection or /{number}/offer-response; successful writes redirect to a GET.
 */
@Controller
@RequestMapping("/member/applications")
public class MemberApplicationPageController {
    private final AdmissionsApplicationService admissions;
    private final ApplicationResultService results;
    private final MemberSessionService sessions;
    private final AcademicYearService years;
    private final ProgrammeLevelService levels;

    public MemberApplicationPageController(AdmissionsApplicationService admissions, ApplicationResultService results,
                                           MemberSessionService sessions, AcademicYearService years,
                                           ProgrammeLevelService levels) {
        this.admissions = admissions;
        this.results = results;
        this.sessions = sessions;
        this.years = years;
        this.levels = levels;
    }

    @GetMapping
    public String list(@RequestParam(defaultValue = "0") int page, HttpServletRequest request, Model model) {
        model.addAttribute("applications", results.memberApplications(sessions.memberId(request), page, 25));
        return "member/applications";
    }

    @GetMapping("/new")
    public String application(HttpServletRequest request, Model model) {
        sessions.memberId(request);
        model.addAttribute("applicationForm", new ApplicationSubmissionRequest(null, "", "", null, "",
                null, "", "", ""));
        formOptions(model);
        return "member/application-form";
    }

    @PostMapping
    public String submit(@Valid @ModelAttribute("applicationForm") ApplicationSubmissionRequest form, BindingResult errors,
                         HttpServletRequest request, HttpServletResponse response, Model model,
                         RedirectAttributes redirect) {
        Long memberId = sessions.memberId(request);
        if (errors.hasErrors()) {
            response.setStatus(400);
            formOptions(model);
            return "member/application-form";
        }
        try {
            admissions.submitApplication(form, memberId);
        } catch (ResponseStatusException ex) {
            if (ex.getStatusCode().value() != 400 && ex.getStatusCode().value() != 409
                    && ex.getStatusCode().value() != 404) throw ex;
            response.setStatus(ex.getStatusCode().value());
            model.addAttribute("error", ex.getReason());
            formOptions(model);
            return "member/application-form";
        }
        redirect.addFlashAttribute("message", "Application submitted.");
        return "redirect:/member/applications";
    }

    @GetMapping("/{number}")
    public String result(@PathVariable String number, HttpServletRequest request, Model model) {
        model.addAttribute("result", results.memberResult(sessions.memberId(request), number));
        return "member/application-result";
    }

    @GetMapping("/{number}/offer")
    public String offer(@PathVariable String number, @RequestParam OfferDecisionRequest.Decision decision,
                        HttpServletRequest request, Model model, RedirectAttributes redirect) {
        try {
            var confirmation = sessions.offer(number, decision, request);
            if (confirmation != null) {
                model.addAttribute("offer", confirmation);
                return "member/offer-confirmation";
            }
            redirect.addFlashAttribute("error", "The offer has expired.");
        } catch (ResponseStatusException ex) {
            if (ex.getStatusCode().value() != 409) throw ex;
            redirect.addFlashAttribute("error", ex.getReason());
        }
        return resultRedirect(number, redirect);
    }

    @PostMapping("/{number}/offer-response")
    public String respond(@PathVariable String number, @Valid @ModelAttribute OfferDecisionRequest form,
                          BindingResult errors, HttpServletRequest request, RedirectAttributes redirect) {
        sessions.memberId(request);
        if (errors.hasErrors()) {
            redirect.addFlashAttribute("error", "Review the offer before confirming your decision.");
            return resultRedirect(number, redirect);
        }
        try {
            if (sessions.respond(number, form, request)) {
                redirect.addFlashAttribute("message", "Your decision was saved and cannot be changed.");
            } else {
                redirect.addFlashAttribute("error", "The offer has expired.");
            }
        } catch (ResponseStatusException ex) {
            if (ex.getStatusCode().value() != 409) throw ex;
            redirect.addFlashAttribute("error", ex.getReason());
        }
        return resultRedirect(number, redirect);
    }

    private String resultRedirect(String number, RedirectAttributes redirect) {
        redirect.addAttribute("number", number);
        return "redirect:/member/applications/{number}";
    }

    private void formOptions(Model model) {
        model.addAttribute("documentTypes", IdentityDocumentType.values());
        try {
            var year = years.getActiveAcademicYear();
            LocalDate today = LocalDate.now(ZoneId.of("Asia/Hong_Kong"));
            model.addAttribute("programmeLevels", levels.list(year.id()).stream()
                    .filter(level -> !today.isBefore(level.applicationStartDate())
                            && !today.isAfter(level.applicationEndDate())).toList());
        } catch (ResponseStatusException ex) {
            if (ex.getStatusCode().value() != 404) throw ex;
            model.addAttribute("programmeLevels", List.of());
            if (!model.containsAttribute("error")) model.addAttribute("error", ex.getReason());
        }
    }
}
