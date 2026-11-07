package hk.edu.sehs4701.cissas.controller.admin;

import hk.edu.sehs4701.cissas.controller.api.admin.IdentityReviewController;
import hk.edu.sehs4701.cissas.service.IdentityReviewService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class IdentityReviewControllerTest {
    private final IdentityReviewService service = mock(IdentityReviewService.class);
    private final IdentityReviewController controller = new IdentityReviewController(service);

    @Test
    void adminCanReview() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpSession session = mock(HttpSession.class);
        when(request.getSession(false)).thenReturn(session);
        when(session.getAttribute("authenticatedUserRole")).thenReturn("ROLE_ADMIN");
        when(session.getAttribute("authenticatedUserId")).thenReturn(42L);
        controller.review(new IdentityReviewController.ReviewRequest("APP-1", "APP-2", true), request);
        verify(service).review(42L, "APP-1", "APP-2", true);
    }

    @Test
    void memberCannotReviewOrList() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpSession session = mock(HttpSession.class);
        when(request.getSession(false)).thenReturn(session);
        when(session.getAttribute("authenticatedUserRole")).thenReturn("ROLE_MEMBER");
        assertEquals(HttpStatus.FORBIDDEN, assertThrows(ResponseStatusException.class,
                () -> controller.pending(null, 25, request)).getStatusCode());
        assertEquals(HttpStatus.FORBIDDEN, assertThrows(ResponseStatusException.class,
                () -> controller.evidence("APP-1", request)).getStatusCode());
        verifyNoInteractions(service);
    }

    @Test
    void adminGetResponsesDisableCachingOverHttp() throws Exception {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("authenticatedUserRole", "ROLE_ADMIN");
        session.setAttribute("authenticatedUserId", 42L);
        var mvc = MockMvcBuilders.standaloneSetup(controller).build();
        for (String path : new String[]{"/api/admin/identity-reviews",
                "/api/admin/identity-reviews/history", "/api/admin/identity-reviews/applications/APP-1"}) {
            mvc.perform(get(path).session(session))
                    .andExpect(status().isOk())
                    .andExpect(header().string("Cache-Control", "no-store"));
        }
    }

    @Test
    void historyReturnsCursorAndForwardsPageParameters() throws Exception {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("authenticatedUserRole", "ROLE_ADMIN");
        session.setAttribute("authenticatedUserId", 42L);
        when(service.history(42L, "42", 1))
                .thenReturn(new IdentityReviewService.HistoryPage(List.of(
                        new IdentityReviewService.Decision("APP-1", "APP-2", true,
                                "9007199254740993", null, true)), "41"));
        MockMvcBuilders.standaloneSetup(controller).build()
                .perform(get("/api/admin/identity-reviews/history").param("cursor", "42").param("size", "1")
                        .session(session))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(content().string(containsString("\"nextCursor\":\"41\"")))
                .andExpect(content().string(containsString("\"reviewedBy\":\"9007199254740993\"")));
        verify(service).history(42L, "42", 1);
    }
}
