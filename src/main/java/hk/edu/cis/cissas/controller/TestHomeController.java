package hk.edu.cis.cissas.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.ui.Model;

@Controller
public class TestHomeController {
    @GetMapping("/")
    public String index(Model model) {
        // Passing dynamic data to the index page
        model.addAttribute("appName", "My Spring Boot App");
        model.addAttribute("username", "Developer");
        return "index"; // Looks for index.html in src/main/resources/templates/
    }
}

