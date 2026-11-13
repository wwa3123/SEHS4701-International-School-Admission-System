package hk.edu.sehs4701.cissas.controller.web;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@Controller
public class MemberPageController {

    @GetMapping("/dashboard")
    public String dashboard() {
        return "member/dashboard";
    }

    @GetMapping("/my-applications")
    public String myApplications() {
        return "member/my-applications";
    }

    @GetMapping("/apply")
    public String apply() {
        return "member/apply";
    }

    @GetMapping("/applications/{applicationNumber}")
    public String applicationDetail(@PathVariable String applicationNumber) {
        return "member/application-detail";
    }
}