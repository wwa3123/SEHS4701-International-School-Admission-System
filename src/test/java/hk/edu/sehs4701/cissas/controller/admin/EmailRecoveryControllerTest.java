package hk.edu.sehs4701.cissas.controller.admin;

import hk.edu.sehs4701.cissas.controller.api.admin.EmailRecoveryController;
import hk.edu.sehs4701.cissas.dto.request.SkipFailedEmailRequest;
import hk.edu.sehs4701.cissas.service.AdminAccountService;
import hk.edu.sehs4701.cissas.service.EmailService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

class EmailRecoveryControllerTest {
    @Test
    void requiresAnAdministratorBeforeResending() {
        AdminAccountService accounts = mock(AdminAccountService.class);
        EmailService emails = mock(EmailService.class);
        var request = new MockHttpServletRequest();
        doThrow(new ResponseStatusException(HttpStatus.FORBIDDEN))
                .when(accounts).requireAdminForUpdate(request);

        assertThrows(ResponseStatusException.class,
                () -> new EmailRecoveryController(accounts, emails).resend(1L, request));
        verifyNoInteractions(emails);
    }

    @Test
    void rejectsResendWhenDeliveryIsNoLongerAllowed() {
        AdminAccountService accounts = mock(AdminAccountService.class);
        EmailService emails = mock(EmailService.class);
        var request = new MockHttpServletRequest();
        doThrow(new IllegalStateException("Already sent")).when(emails).manualResend(1L);

        var exception = assertThrows(ResponseStatusException.class,
                () -> new EmailRecoveryController(accounts, emails).resend(1L, request));
        assertEquals(HttpStatus.CONFLICT, exception.getStatusCode());
    }

    @Test
    void skipRequiresAdministratorAndRecordsTheirId() {
        AdminAccountService accounts = mock(AdminAccountService.class);
        EmailService emails = mock(EmailService.class);
        var request = new MockHttpServletRequest();
        var body = new SkipFailedEmailRequest("Undeliverable");
        doThrow(new ResponseStatusException(HttpStatus.FORBIDDEN))
                .when(accounts).requireAdminForUpdate(request);
        var controller = new EmailRecoveryController(accounts, emails);
        assertThrows(ResponseStatusException.class, () -> controller.skip(1L, body, request));
        verifyNoInteractions(emails);

        reset(accounts);
        when(accounts.requireAdminForUpdate(request)).thenReturn(42L);
        controller.skip(1L, body, request);
        verify(emails).skipFailedApplicationEmail(1L, 42L, "Undeliverable");
    }
}
