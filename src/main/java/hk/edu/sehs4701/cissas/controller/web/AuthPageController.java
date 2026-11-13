package hk.edu.sehs4701.cissas.controller.web;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class AuthPageController {

    @GetMapping("/register")
    public String register() {
        return "redirect:/membership/register";
    }

    @GetMapping("/verify-email")
    public String verifyEmail(@RequestParam(required = false) String email, RedirectAttributes redirect) {
        if (email != null) redirect.addAttribute("email", email);
        return "redirect:/membership/verify-email";
    }

    @GetMapping("/complete-registration")
    public String completeRegistration() {
        return "redirect:/membership/complete-registration";
    }
}