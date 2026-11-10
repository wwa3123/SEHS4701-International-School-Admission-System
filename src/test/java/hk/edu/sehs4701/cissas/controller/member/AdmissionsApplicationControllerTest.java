package hk.edu.sehs4701.cissas.controller.member;

import hk.edu.sehs4701.cissas.controller.api.member.AdmissionsApplicationController;
import hk.edu.sehs4701.cissas.dto.request.ApplicationSubmissionRequest;
import hk.edu.sehs4701.cissas.dto.request.OfferDecisionRequest;
import hk.edu.sehs4701.cissas.dto.response.OfferConfirmation;
import hk.edu.sehs4701.cissas.enums.IdentityDocumentType;
import hk.edu.sehs4701.cissas.enums.LevelCode;
import hk.edu.sehs4701.cissas.service.AdmissionsApplicationService;
import hk.edu.sehs4701.cissas.service.ApplicationResultService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpSession;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

class AdmissionsApplicationControllerTest {
    private final AdmissionsApplicationService service = mock(AdmissionsApplicationService.class);
    private final ApplicationResultService results = mock(ApplicationResultService.class);
    private final AdmissionsApplicationController controller = new AdmissionsApplicationController(service, results);
    private final ApplicationSubmissionRequest request = new ApplicationSubmissionRequest(
            LevelCode.S1,
            "Applicant Name",
            "A123456(3)",
            IdentityDocumentType.HKID,
            null,
            LocalDate.of(2018, 1, 1),
            "Unspecified",
            "12345678",
            "Previous School"
    );

    @Test
    void submitsForMemberStoredInSession() {
        HttpServletRequest servletRequest = mock(HttpServletRequest.class);
        HttpSession session = mock(HttpSession.class);
        when(servletRequest.getSession(false)).thenReturn(session);
        when(session.getAttribute("authenticatedUserRole")).thenReturn("ROLE_MEMBER");
        when(session.getAttribute("authenticatedUserId")).thenReturn(123L);

        controller.submit(request, servletRequest);

        verify(service).submitApplication(request, 123L);
    }

    @Test
    void rejectsMissingSession() {
        HttpServletRequest servletRequest = mock(HttpServletRequest.class);
        when(servletRequest.getSession(false)).thenReturn(null);

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> controller.submit(request, servletRequest)
        );

        assertEquals(HttpStatus.UNAUTHORIZED, exception.getStatusCode());
        verifyNoInteractions(service);
    }

    @Test
    void rejectsNonMemberSession() {
        HttpServletRequest servletRequest = mock(HttpServletRequest.class);
        HttpSession session = mock(HttpSession.class);
        when(servletRequest.getSession(false)).thenReturn(session);
        when(session.getAttribute("authenticatedUserRole")).thenReturn("ROLE_ADMIN");

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> controller.submit(request, servletRequest)
        );

        assertEquals(HttpStatus.FORBIDDEN, exception.getStatusCode());
        verifyNoInteractions(service);
    }

    @Test
    void decisionRequiresReviewedOfferAndMatchingToken() {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("authenticatedUserRole", "ROLE_MEMBER");
        session.setAttribute("authenticatedUserId", 123L);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setSession(session);
        OfferDecisionRequest unconfirmed = new OfferDecisionRequest(OfferDecisionRequest.Decision.Accept, "wrong");
        assertEquals(HttpStatus.CONFLICT, assertThrows(ResponseStatusException.class,
                () -> controller.respond("APP-1", unconfirmed, request)).getStatusCode());
        LocalDateTime deadline = LocalDateTime.now().plusDays(1);
        when(results.offer(123L, "APP-1", OfferDecisionRequest.Decision.Accept)).thenReturn(
                new OfferConfirmation("S1", "APP-1", deadline, null, OfferDecisionRequest.Decision.Accept, null));
        OfferConfirmation confirmation = controller.offer("APP-1", OfferDecisionRequest.Decision.Accept, request).getBody();
        assertEquals(OfferDecisionRequest.Decision.Accept, confirmation.decision());
        assertThrows(ResponseStatusException.class, () -> controller.respond("APP-1",
                new OfferDecisionRequest(OfferDecisionRequest.Decision.Reject, confirmation.confirmationToken()), request));
        verify(results, never()).respondToOffer(anyLong(), anyString(), any(), any());
        when(results.respondToOffer(123L, "APP-1", OfferDecisionRequest.Decision.Accept, confirmation))
                .thenReturn(true);
        assertEquals(HttpStatus.NO_CONTENT, controller.respond("APP-1",
                new OfferDecisionRequest(OfferDecisionRequest.Decision.Accept, confirmation.confirmationToken()),
                request).getStatusCode());
        assertThrows(ResponseStatusException.class, () -> controller.respond("APP-1",
                new OfferDecisionRequest(OfferDecisionRequest.Decision.Accept, confirmation.confirmationToken()), request));
    }

    @Test
    void expiredOfferReturnsConflictWithoutCreatingConfirmation() {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("authenticatedUserRole", "ROLE_MEMBER");
        session.setAttribute("authenticatedUserId", 123L);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setSession(session);

        assertEquals(HttpStatus.CONFLICT,
                controller.offer("APP-1", OfferDecisionRequest.Decision.Accept, request).getStatusCode());
        assertNull(session.getAttribute("offerConfirmation"));
    }

    @Test
    void applicationListRequiresMemberSession() {
        MockHttpServletRequest request = new MockHttpServletRequest();

        assertEquals(HttpStatus.UNAUTHORIZED, assertThrows(ResponseStatusException.class,
                () -> controller.applications(0, 25, request)).getStatusCode());
        verifyNoInteractions(results);
    }
}
