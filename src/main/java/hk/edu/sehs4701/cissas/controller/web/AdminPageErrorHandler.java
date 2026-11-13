package hk.edu.sehs4701.cissas.controller.web;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.server.ResponseStatusException;

@ControllerAdvice(assignableTypes = {AdminApplicationPageController.class, AdminEmailPageController.class})
public class AdminPageErrorHandler {
    @ExceptionHandler(ResponseStatusException.class)
    public String businessError(ResponseStatusException ex, HttpServletResponse response, Model model) {
        response.setStatus(ex.getStatusCode().value());
        model.addAttribute("error", ex.getReason());
        return "admin/action-error";
    }

}
