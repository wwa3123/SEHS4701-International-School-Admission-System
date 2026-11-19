package hk.edu.sehs4701.cissas.controller.web;

import hk.edu.sehs4701.cissas.dto.request.MemberIdentityDocumentRequest;
import hk.edu.sehs4701.cissas.enums.IdentityDocumentType;
import hk.edu.sehs4701.cissas.service.MemberIdentityDocumentService;
import hk.edu.sehs4701.cissas.service.MemberSessionService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/member/identity-documents")
public class MemberIdentityDocumentPageController {
    private final MemberIdentityDocumentService documents;
    private final MemberSessionService sessions;

    public MemberIdentityDocumentPageController(MemberIdentityDocumentService documents, MemberSessionService sessions) {
        this.documents = documents;
        this.sessions = sessions;
    }

    @GetMapping
    public String list(HttpServletRequest request, Model model) {
        populate(sessions.memberId(request), model);
        return "member/identity-documents";
    }

    @PostMapping
    public String add(@Valid @ModelAttribute("documentForm") MemberIdentityDocumentRequest form,
                      BindingResult errors, HttpServletRequest request, HttpServletResponse response,
                      Model model, RedirectAttributes redirect) {
        Long memberId = sessions.memberId(request);
        if (errors.hasErrors()) {
            response.setStatus(HttpStatus.BAD_REQUEST.value());
            populate(memberId, model);
            return "member/identity-documents";
        }
        try {
            documents.add(memberId, form);
        } catch (DataIntegrityViolationException exception) {
            response.setStatus(HttpStatus.CONFLICT.value());
            populate(memberId, model);
            model.addAttribute("error", "Identity document is already linked to an account");
            return "member/identity-documents";
        } catch (ResponseStatusException exception) {
            int status = exception.getStatusCode().value();
            if (status != 400 && status != 409) throw exception;
            response.setStatus(status);
            populate(memberId, model);
            model.addAttribute("error", exception.getReason());
            return "member/identity-documents";
        }
        redirect.addFlashAttribute("message", "Identity document added.");
        return "redirect:/member/identity-documents";
    }

    @PostMapping("/{documentId}/primary")
    public String setPrimary(@PathVariable Long documentId, HttpServletRequest request,
                             RedirectAttributes redirect) {
        documents.setPrimary(sessions.memberId(request), documentId);
        redirect.addFlashAttribute("message", "Primary identity document updated.");
        return "redirect:/member/identity-documents";
    }

    private void populate(Long memberId, Model model) {
        model.addAttribute("identityDocuments", documents.list(memberId));
        model.addAttribute("documentTypes", IdentityDocumentType.values());
        if (!model.containsAttribute("documentForm")) {
            model.addAttribute("documentForm", new MemberIdentityDocumentRequest(null, "", ""));
        }
    }
}
