package hk.edu.sehs4701.cissas.service;

import hk.edu.sehs4701.cissas.repository.RegistrationRateLimitRepository;
import hk.edu.sehs4701.cissas.security.HashService;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

class RegistrationRateLimitServiceTest {
    @Test
    void rejectsRequestsAboveThePerAddressLimit() {
        RegistrationRateLimitRepository repository = mock(RegistrationRateLimitRepository.class);
        HashService hashService = mock(HashService.class);
        when(hashService.hashRegistrationSource("192.0.2.1")).thenReturn("hashed-address");
        when(repository.findRequestCount("hashed-address")).thenReturn(11);
        RegistrationRateLimitService service = new RegistrationRateLimitService(repository, hashService, "");

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> service.checkAllowed("192.0.2.1")
        );

        assertEquals(429, exception.getStatusCode().value());
        verify(repository).incrementWindowCount(anyString(), any(), any());
    }

    @Test
    void trustsForwardedAddressOnlyFromConfiguredProxy() {
        RegistrationRateLimitService service = new RegistrationRateLimitService(
                mock(RegistrationRateLimitRepository.class), mock(HashService.class), "10.0.0.5"
        );
        HttpServletRequest proxyRequest = mock(HttpServletRequest.class);
        when(proxyRequest.getRemoteAddr()).thenReturn("10.0.0.5");
        when(proxyRequest.getHeader("X-Forwarded-For")).thenReturn("192.0.2.17");

        assertEquals("192.0.2.17", service.resolveClientAddress(proxyRequest));

        HttpServletRequest directRequest = mock(HttpServletRequest.class);
        when(directRequest.getRemoteAddr()).thenReturn("198.51.100.8");
        when(directRequest.getHeader("X-Forwarded-For")).thenReturn("192.0.2.17");
        assertEquals("198.51.100.8", service.resolveClientAddress(directRequest));
    }
}
