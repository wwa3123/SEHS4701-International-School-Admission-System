package hk.edu.sehs4701.cissas.service;

import hk.edu.sehs4701.cissas.dto.request.OfferDecisionRequest;
import hk.edu.sehs4701.cissas.dto.response.OfferConfirmation;
import hk.edu.sehs4701.cissas.enums.ActorRole;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

@Service
public class MemberSessionService {
    private static final String OFFER_CONFIRMATION = "offerConfirmation";
    private final ApplicationResultService results;

    public MemberSessionService(ApplicationResultService results) {
        this.results = results;
    }

    public Long memberId(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Member login required");
        }
        if (!ActorRole.ROLE_MEMBER.name().equals(session.getAttribute("authenticatedUserRole"))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Member access required");
        }
        if (!(session.getAttribute("authenticatedUserId") instanceof Long id)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Member login required");
        }
        return id;
    }

    public OfferConfirmation offer(String number, OfferDecisionRequest.Decision decision, HttpServletRequest request) {
        OfferConfirmation offer = results.offer(memberId(request), number, decision);
        if (offer == null) return null;
        OfferConfirmation confirmation = new OfferConfirmation(offer.programmeLevel(), offer.applicationNumber(),
                offer.acceptanceDeadline(), offer.resultReleasedAt(), decision, UUID.randomUUID().toString());
        request.getSession(false).setAttribute(OFFER_CONFIRMATION, confirmation);
        return confirmation;
    }

    public boolean respond(String number, OfferDecisionRequest decision, HttpServletRequest request) {
        Long id = memberId(request);
        HttpSession session = request.getSession(false);
        if (!(session.getAttribute(OFFER_CONFIRMATION) instanceof OfferConfirmation confirmation)
                || !confirmation.applicationNumber().equals(number)
                || confirmation.decision() != decision.decision()
                || !confirmation.confirmationToken().equals(decision.confirmationToken())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Review the offer before confirming your decision");
        }
        boolean saved = results.respondToOffer(id, number, decision.decision(), confirmation);
        session.removeAttribute(OFFER_CONFIRMATION);
        return saved;
    }
}
