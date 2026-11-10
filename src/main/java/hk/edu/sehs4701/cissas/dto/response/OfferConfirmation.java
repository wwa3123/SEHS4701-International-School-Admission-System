package hk.edu.sehs4701.cissas.dto.response;

import hk.edu.sehs4701.cissas.dto.request.OfferDecisionRequest;
import java.time.LocalDateTime;

public record OfferConfirmation(String programmeLevel, String applicationNumber, LocalDateTime acceptanceDeadline,
                                LocalDateTime resultReleasedAt, OfferDecisionRequest.Decision decision,
                                String confirmationToken) {
}
