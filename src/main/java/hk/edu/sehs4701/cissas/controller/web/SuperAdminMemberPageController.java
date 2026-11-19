package hk.edu.sehs4701.cissas.controller.web;

import hk.edu.sehs4701.cissas.service.AdminAccountService;
import hk.edu.sehs4701.cissas.service.MemberAccountService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.server.ResponseStatusException;

@Controller
@RequestMapping("/super-admin/members")
@Transactional(isolation = Isolation.READ_COMMITTED)
public class SuperAdminMemberPageController {
    private static final int PAGE_SIZE = 50;
    private final AdminAccountService accounts;
    private final MemberAccountService members;

    public SuperAdminMemberPageController(AdminAccountService accounts, MemberAccountService members) {
        this.accounts = accounts;
        this.members = members;
    }

    @GetMapping
    public String list(@RequestParam(defaultValue = "0") int page, HttpServletRequest request,
                       HttpServletResponse response, Model model) {
        guard(request, response);
        if (page < 0) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid page");
        model.addAttribute("members", members.list(PageRequest.of(page, PAGE_SIZE)));
        return "super-admin/members";
    }

    @PostMapping("/{id}/deactivate")
    public String deactivate(@PathVariable Long id, HttpServletRequest request, HttpServletResponse response,
                             RedirectAttributes redirect) {
        guard(request, response);
        members.deactivate(id);
        redirect.addFlashAttribute("message", "Member account deactivated.");
        return "redirect:/super-admin/members";
    }

    private void guard(HttpServletRequest request, HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-store");
        accounts.requireSuperAdminForUpdate(request);
    }
}
