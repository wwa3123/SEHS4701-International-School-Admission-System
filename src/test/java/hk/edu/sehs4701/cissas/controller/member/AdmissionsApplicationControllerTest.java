package hk.edu.sehs4701.cissas.controller.member;

import hk.edu.sehs4701.cissas.dto.request.ApplicationSubmissionRequest;
import hk.edu.sehs4701.cissas.enums.IdentityDocumentType;
import hk.edu.sehs4701.cissas.enums.LevelCode;
import hk.edu.sehs4701.cissas.service.AdmissionsApplicationService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

class AdmissionsApplicationControllerTest {
    private final AdmissionsApplicationService service = mock(AdmissionsApplicationService.class);
    private final AdmissionsApplicationController controller = new AdmissionsApplicationController(service);
    private final ApplicationSubmissionRequest request = new ApplicationSubmissionRequest(
            LevelCode.S1,
            "Applicant Name",
            "A123456(8)",
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
}
