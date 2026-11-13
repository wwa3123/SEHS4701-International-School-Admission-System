package hk.edu.sehs4701.cissas.controller.web;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@Controller
public class MemberPageController {

    @GetMapping("/dashboard")
    public String dashboard() {
        return "redirect:/member/applications";
    }

    @GetMapping("/my-applications")
    public String myApplications() {
        return "redirect:/member/applications";
    }

    @GetMapping("/apply")
    public String apply() {
        return "redirect:/member/applications/new";
    }

    @GetMapping("/applications/{applicationNumber}")
    public String applicationDetail(@PathVariable String applicationNumber) {
        return "redirect:/member/applications/{applicationNumber}";
    }
}