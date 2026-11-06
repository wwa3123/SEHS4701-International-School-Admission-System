package hk.edu.sehs4701.cissas.controller.pub;

import hk.edu.sehs4701.cissas.dto.request.MembershipRegistrationRequest;
import hk.edu.sehs4701.cissas.enums.IdentityDocumentType;
import hk.edu.sehs4701.cissas.service.MembershipRegistrationService;
import hk.edu.sehs4701.cissas.service.RateLimitService;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

class MembershipRegistrationControllerTest {
    @Test
    void duplicateKeyRaceReturnsGenericAcceptedResponse() {
        MembershipRegistrationService registrationService = mock(MembershipRegistrationService.class);
        RateLimitService rateLimitService = mock(RateLimitService.class);
        MembershipRegistrationController controller = new MembershipRegistrationController(
                registrationService, rateLimitService
        );
        doThrow(new DataIntegrityViolationException("duplicate key", new SQLException("duplicate", "23000", 1062)))
                .when(registrationService).register(any());

        var response = controller.register(validRequest(), null);

        assertEquals("ACCEPTED", response.status());
        assertEquals(
                "If this email can be registered, verification instructions will be sent",
                response.message()
        );
    }

    @Test
    void nonDuplicateIntegrityErrorsAreNotHidden() {
        MembershipRegistrationService registrationService = mock(MembershipRegistrationService.class);
        RateLimitService rateLimitService = mock(RateLimitService.class);
        MembershipRegistrationController controller = new MembershipRegistrationController(
                registrationService, rateLimitService
        );
        DataIntegrityViolationException failure =
                new DataIntegrityViolationException("not null violation", new SQLException("not null", "23000", 1048));
        doThrow(failure).when(registrationService).register(any());

        assertEquals(failure, assertThrows(
                DataIntegrityViolationException.class,
                () -> controller.register(validRequest(), null)
        ));
    }

    private MembershipRegistrationRequest validRequest() {
        return new MembershipRegistrationRequest(
                "Test", "Member", "member@example.com", "12345678",
                IdentityDocumentType.HKID, "A123456(3)", null, "secure-password"
        );
    }
}
