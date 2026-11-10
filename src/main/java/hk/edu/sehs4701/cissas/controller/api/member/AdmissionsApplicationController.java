package hk.edu.sehs4701.cissas.controller.api.member;

import hk.edu.sehs4701.cissas.dto.request.ApplicationSubmissionRequest;
import hk.edu.sehs4701.cissas.dto.request.OfferDecisionRequest;
import hk.edu.sehs4701.cissas.dto.response.OfferConfirmation;
import hk.edu.sehs4701.cissas.dto.response.MemberApplicationResult;
import hk.edu.sehs4701.cissas.dto.response.MemberApplication;
import hk.edu.sehs4701.cissas.enums.ActorRole;
import hk.edu.sehs4701.cissas.service.AdmissionsApplicationService;
import hk.edu.sehs4701.cissas.service.ApplicationResultService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.data.domain.Slice;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import java.util.UUID;

@RestController
@RequestMapping("/api/member/applications")
public class AdmissionsApplicationController {
    private static final String OFFER_CONFIRMATION = "offerConfirmation";
    private final AdmissionsApplicationService admissionsApplicationService;
    private final ApplicationResultService applicationResultService;

    public AdmissionsApplicationController(AdmissionsApplicationService admissionsApplicationService,
                                           ApplicationResultService applicationResultService) {
        this.admissionsApplicationService = admissionsApplicationService;
        this.applicationResultService = applicationResultService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public void submit(
            @Valid @RequestBody ApplicationSubmissionRequest request,
            HttpServletRequest servletRequest) {
        admissionsApplicationService.submitApplication(request, memberId(servletRequest));
    }

    @GetMapping
    public ResponseEntity<Slice<MemberApplication>> applications(
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "25") int size,
            HttpServletRequest request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(applicationResultService.memberApplications(memberId(request), page, size));
    }

    @GetMapping("/{applicationNumber}/offer")
    public ResponseEntity<OfferConfirmation> offer(@PathVariable String applicationNumber,
                                                   @RequestParam OfferDecisionRequest.Decision decision,
                                                   HttpServletRequest servletRequest) {
        OfferConfirmation offer = applicationResultService.offer(memberId(servletRequest), applicationNumber, decision);
        if (offer == null) {
            return ResponseEntity.status(HttpStatus.CONFLICT).build();
        }
        OfferConfirmation confirmation = new OfferConfirmation(offer.programmeLevel(), offer.applicationNumber(),
                offer.acceptanceDeadline(), offer.resultReleasedAt(), decision, UUID.randomUUID().toString());
        servletRequest.getSession(false).setAttribute(OFFER_CONFIRMATION, confirmation);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(confirmation);
    }

    @GetMapping("/{applicationNumber}/result")
    public ResponseEntity<MemberApplicationResult> result(@PathVariable String applicationNumber,
                                                           HttpServletRequest servletRequest) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(applicationResultService.memberResult(memberId(servletRequest), applicationNumber));
    }

    @PutMapping("/{applicationNumber}/offer-response")
    public ResponseEntity<Void> respond(@PathVariable String applicationNumber,
                                        @Valid @RequestBody OfferDecisionRequest request,
                                        HttpServletRequest servletRequest) {
        Long memberId = memberId(servletRequest);
        HttpSession session = servletRequest.getSession(false);
        Object pending = session.getAttribute(OFFER_CONFIRMATION);
        if (!(pending instanceof OfferConfirmation confirmation)
                || !confirmation.applicationNumber().equals(applicationNumber)
                || confirmation.decision() != request.decision()
                || !confirmation.confirmationToken().equals(request.confirmationToken())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Review the offer before confirming your decision");
        }
        if (!applicationResultService.respondToOffer(memberId, applicationNumber, request.decision(), confirmation)) {
            session.removeAttribute(OFFER_CONFIRMATION);
            return ResponseEntity.status(HttpStatus.CONFLICT).build();
        }
        session.removeAttribute(OFFER_CONFIRMATION);
        return ResponseEntity.noContent().build();
    }

    private Long memberId(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Member login required");
        }
        if (!ActorRole.ROLE_MEMBER.name().equals(session.getAttribute("authenticatedUserRole"))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Member access required");
        }
        Object id = session.getAttribute("authenticatedUserId");
        if (!(id instanceof Long memberId)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Member login required");
        }
        return memberId;
    }
}
