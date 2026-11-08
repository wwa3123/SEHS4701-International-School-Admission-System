package hk.edu.sehs4701.cissas.controller.pub;

import hk.edu.sehs4701.cissas.controller.api.pub.MembershipRegistrationController;
import hk.edu.sehs4701.cissas.dto.request.MembershipEmailRequest;
import hk.edu.sehs4701.cissas.dto.request.MembershipRegistrationRequest;
import hk.edu.sehs4701.cissas.dto.request.VerifyMembershipEmailRequest;
import hk.edu.sehs4701.cissas.enums.IdentityDocumentType;
import hk.edu.sehs4701.cissas.service.MembershipRegistrationService;
import hk.edu.sehs4701.cissas.service.RateLimitService;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.server.ResponseStatusException;

import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class MembershipRegistrationControllerTest {
    private final MembershipRegistrationService service = mock(MembershipRegistrationService.class);
    private final RateLimitService limits = mock(RateLimitService.class);
    private final MembershipRegistrationController controller = new MembershipRegistrationController(service, limits);

    @Test
    void duplicateKeyRaceReturnsGenericAcceptedResponse() {
        doThrow(new DataIntegrityViolationException("duplicate", new SQLException("duplicate", "23000", 1062)))
                .when(service).register(any());
        assertEquals("ACCEPTED", controller.register(new MembershipEmailRequest("member@example.com"),
                new MockHttpServletRequest()).status());
    }

    @Test
    void nonDuplicateIntegrityErrorsAreNotHidden() {
        DataIntegrityViolationException failure = new DataIntegrityViolationException("not null",
                new SQLException("not null", "23000", 1048));
        doThrow(failure).when(service).register(any());
        assertSame(failure, assertThrows(DataIntegrityViolationException.class,
                () -> controller.register(new MembershipEmailRequest("member@example.com"), new MockHttpServletRequest())));
    }

    @Test
    void completionRequiresVerifiedSessionAndConsumesItOnSuccess() {
        MockHttpServletRequest browser = new MockHttpServletRequest();
        assertThrows(ResponseStatusException.class, () -> controller.completeRegistration(details(), browser));
        assertEquals("EMAIL_VERIFIED", controller.verifyEmail(
                new VerifyMembershipEmailRequest("MEMBER@example.com", "123456"), browser).status());
        MockHttpSession session = (MockHttpSession) browser.getSession(false);
        MockHttpServletRequest otherBrowser = new MockHttpServletRequest();
        assertThrows(ResponseStatusException.class, () -> controller.completeRegistration(details(), otherBrowser));
        assertEquals("ACTIVE", controller.completeRegistration(details(), browser).status());
        verify(service).completeRegistration("member@example.com", details());
        assertThrows(ResponseStatusException.class, () -> controller.completeRegistration(details(), browser));
        assertNull(session.getAttribute("registrationVerifiedEmail"));
    }

    @Test
    void failedVerificationRemovesPreviousProof() {
        MockHttpServletRequest browser = new MockHttpServletRequest();
        var request = new VerifyMembershipEmailRequest("member@example.com", "123456");
        controller.verifyEmail(request, browser);
        doThrow(new IllegalArgumentException()).when(service).verifyEmail("member@example.com", "123456");
        assertThrows(ResponseStatusException.class, () -> controller.verifyEmail(request, browser));
        assertThrows(ResponseStatusException.class, () -> controller.completeRegistration(details(), browser));
    }

    @Test
    void expiredProofCannotComplete() {
        MockHttpServletRequest browser = new MockHttpServletRequest();
        controller.verifyEmail(new VerifyMembershipEmailRequest("member@example.com", "123456"), browser);
        browser.getSession().setAttribute("registrationVerifiedUntil", java.time.Instant.now().minusSeconds(1));
        assertThrows(ResponseStatusException.class, () -> controller.completeRegistration(details(), browser));
        verify(service, never()).completeRegistration(any(), any());
    }

    @Test
    void emailFirstEndpointValidatesRequests() throws Exception {
        var mvc = MockMvcBuilders.standaloneSetup(controller).build();
        mvc.perform(post("/api/public/membership/register")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"email\":\"member@example.com\"}"))
                .andExpect(status().isAccepted());
        mvc.perform(post("/api/public/membership/register")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"email\":\"not-an-email\"}"))
                .andExpect(status().isBadRequest());
        verify(service).register(new MembershipEmailRequest("member@example.com"));
    }

    @Test
    void documentRaceReturnsConflictAndKeepsProofForRetry() {
        MockHttpServletRequest browser = new MockHttpServletRequest();
        controller.verifyEmail(new VerifyMembershipEmailRequest("member@example.com", "123456"), browser);
        doThrow(new DataIntegrityViolationException("duplicate", new SQLException("duplicate", "23000", 1062)))
                .doNothing().when(service).completeRegistration(anyString(), any());
        assertEquals(409, assertThrows(ResponseStatusException.class,
                () -> controller.completeRegistration(details(), browser)).getStatusCode().value());
        assertEquals("ACTIVE", controller.completeRegistration(details(), browser).status());
    }

    private MembershipRegistrationRequest details() {
        return new MembershipRegistrationRequest("Test", "Member", "member@example.com", "12345678",
                IdentityDocumentType.HKID, "A123456(3)", null, "secure-password");
    }
}
