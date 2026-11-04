package hk.edu.sehs4701.cissas.controller.pub;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.ui.Model;

@Controller
public class DummyHomeController {
    @GetMapping("/")
    public String index(Model model) {
        model.addAttribute("appName", "My Spring Boot App");
        model.addAttribute("username", "Developer");
        return "index";
    }
}

