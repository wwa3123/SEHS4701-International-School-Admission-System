package hk.edu.sehs4701.cissas.controller.member;

import hk.edu.sehs4701.cissas.dto.request.ApplicationSubmissionRequest;
import hk.edu.sehs4701.cissas.enums.ActorRole;
import hk.edu.sehs4701.cissas.service.AdmissionsApplicationService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/member/applications")
public class AdmissionsApplicationController {
    private final AdmissionsApplicationService admissionsApplicationService;

    public AdmissionsApplicationController(AdmissionsApplicationService admissionsApplicationService) {
        this.admissionsApplicationService = admissionsApplicationService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public void submit(
            @Valid @RequestBody ApplicationSubmissionRequest request,
            HttpServletRequest servletRequest) {
        HttpSession session = servletRequest.getSession(false);
        if (session == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Member login required");
        }

        if (!ActorRole.ROLE_MEMBER.name().equals(session.getAttribute("authenticatedUserRole"))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Member access required");
        }

        Object authenticatedUserId = session.getAttribute("authenticatedUserId");
        if (!(authenticatedUserId instanceof Long memberId)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Member login required");
        }

        admissionsApplicationService.submitApplication(request, memberId);
    }
}
