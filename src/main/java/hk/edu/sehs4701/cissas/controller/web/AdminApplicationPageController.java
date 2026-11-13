package hk.edu.sehs4701.cissas.controller.web;

import hk.edu.sehs4701.cissas.dto.request.*;
import hk.edu.sehs4701.cissas.enums.ApplicationStatus;
import hk.edu.sehs4701.cissas.service.AdminAccountService;
import hk.edu.sehs4701.cissas.service.ApplicationResultService;
import hk.edu.sehs4701.cissas.service.IdentityReviewService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/admin/applications")
@Transactional(isolation = Isolation.READ_COMMITTED)
public class AdminApplicationPageController {
    private final ApplicationResultService results;
    private final AdminAccountService accounts;
    private final IdentityReviewService duplicates;

    public AdminApplicationPageController(ApplicationResultService results, AdminAccountService accounts,
                                          IdentityReviewService duplicates) {
        this.results = results;
        this.accounts = accounts;
        this.duplicates = duplicates;
    }

    @GetMapping
    public String applications(@RequestParam(required = false) Long programmeLevelId,
                               @RequestParam(required = false) ApplicationStatus status,
                               @RequestParam(defaultValue = "0") int page, HttpServletRequest request, Model model) {
        model.addAttribute("applications", results.listApplications(
                accounts.requireAdminForUpdate(request), programmeLevelId, status, page, 25));
        model.addAttribute("programmeLevelId", programmeLevelId);
        model.addAttribute("status", status);
        model.addAttribute("statuses", ApplicationStatus.values());
        return "admin/applications";
    }

    @GetMapping("/{number}/result")
    public String result(@PathVariable String number, HttpServletRequest request, Model model) {
        accounts.requireAdminForUpdate(request);
        model.addAttribute("resultForm", new SetApplicationResultRequest(null, ""));
        model.addAttribute("number", number);
        return "admin/result-form";
    }

    @PostMapping("/{number}/result")
    public String result(@PathVariable String number, @Valid @ModelAttribute("resultForm") SetApplicationResultRequest form,
                         BindingResult errors, HttpServletRequest request, HttpServletResponse response,
                         Model model, RedirectAttributes redirect) {
        Long adminId = accounts.requireAdminForUpdate(request);
        model.addAttribute("number", number);
        if (errors.hasErrors()) {
            response.setStatus(400);
            return "admin/result-form";
        }
        if (!results.setResult(adminId, number, form.result(), form.reason())) {
            response.setStatus(409);
            model.addAttribute("error", "Result not saved: the seat quota is full or the offer has expired. Check the current application status.");
            return "admin/result-form";
        }
        redirect.addFlashAttribute("message", "Result released and its email queued.");
        return "redirect:/admin/applications";
    }

    @GetMapping("/{number}/acceptance-deadline")
    public String deadline(@PathVariable String number, HttpServletRequest request, Model model) {
        accounts.requireAdminForUpdate(request);
        model.addAttribute("deadlineForm", new ExtendOfferDeadlineRequest(null, ""));
        model.addAttribute("number", number);
        return "admin/deadline-form";
    }

    @PostMapping("/{number}/acceptance-deadline")
    public String deadline(@PathVariable String number,
                           @Valid @ModelAttribute("deadlineForm") ExtendOfferDeadlineRequest form, BindingResult errors,
                           HttpServletRequest request, HttpServletResponse response, Model model, RedirectAttributes redirect) {
        Long adminId = accounts.requireAdminForUpdate(request);
        model.addAttribute("number", number);
        if (errors.hasErrors()) {
            response.setStatus(400);
            return "admin/deadline-form";
        }
        if (!results.extendDeadline(adminId, number, form.acceptanceDeadline(), form.reason())) {
            response.setStatus(409);
            model.addAttribute("error", "The offer has expired and cannot be extended.");
            return "admin/deadline-form";
        }
        redirect.addFlashAttribute("message", "Acceptance deadline extended.");
        return "redirect:/admin/applications";
    }

    @GetMapping("/waiting-list")
    public String waitingList(@RequestParam Long programmeLevelId, HttpServletRequest request, Model model) {
        model.addAttribute("waitingList", results.waitingList(accounts.requireAdminForUpdate(request), programmeLevelId));
        model.addAttribute("programmeLevelId", programmeLevelId);
        return "admin/waiting-list";
    }

    @GetMapping("/possible-duplicates")
    public String duplicates(@RequestParam(required = false) String cursor, HttpServletRequest request, Model model) {
        model.addAttribute("candidates", duplicates.pending(accounts.requireAdminForUpdate(request), cursor, 25));
        return "admin/duplicates";
    }

    @GetMapping("/possible-duplicates/history")
    public String history(@RequestParam(required = false) String cursor, HttpServletRequest request, Model model) {
        model.addAttribute("history", duplicates.history(accounts.requireAdminForUpdate(request), cursor, 25));
        return "admin/duplicate-history";
    }

    @GetMapping("/possible-duplicates/applications/{number}")
    public String duplicateEvidence(@PathVariable String number, HttpServletRequest request, Model model) {
        model.addAttribute("evidence", duplicates.evidence(accounts.requireAdminForUpdate(request), number));
        return "admin/duplicate-evidence";
    }

    @GetMapping("/possible-duplicates/review")
    public String review(@RequestParam String duplicateApplicationNumber, @RequestParam String matchingApplicationNumber,
                         HttpServletRequest request, Model model) {
        Long adminId = accounts.requireAdminForUpdate(request);
        model.addAttribute("flagged", duplicates.evidence(adminId, duplicateApplicationNumber));
        model.addAttribute("match", duplicates.evidence(adminId, matchingApplicationNumber));
        model.addAttribute("reviewForm", new ApplicationDuplicateReviewRequest(
                duplicateApplicationNumber, matchingApplicationNumber, null));
        return "admin/duplicate-review";
    }

    @PostMapping("/possible-duplicates")
    public String review(@Valid @ModelAttribute("reviewForm") ApplicationDuplicateReviewRequest form, BindingResult errors,
                         HttpServletRequest request, HttpServletResponse response, Model model, RedirectAttributes redirect) {
        Long adminId = accounts.requireAdminForUpdate(request);
        if (errors.hasErrors()) {
            response.setStatus(400);
            return "admin/duplicate-review";
        }
        duplicates.review(adminId, form.duplicateApplicationNumber(), form.matchingApplicationNumber(), form.samePerson());
        redirect.addFlashAttribute("message", "Duplicate review saved.");
        return "redirect:/admin/applications/possible-duplicates";
    }
}
