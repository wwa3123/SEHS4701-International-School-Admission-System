package hk.edu.sehs4701.cissas.service;

import hk.edu.sehs4701.cissas.repository.RateLimitRepository;
import hk.edu.sehs4701.cissas.security.HashService;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

class RateLimitServiceTest {
    @Test
    void rejectsRequestsAboveThePerAddressLimit() {
        RateLimitRepository repository = mock(RateLimitRepository.class);
        HashService hashService = mock(HashService.class);
        when(hashService.hashRateLimitKey("192.0.2.1")).thenReturn("hashed-address");
        when(repository.findRequestCount("hashed-address")).thenReturn(11);
        RateLimitService service = new RateLimitService(repository, hashService, "", 10, 100);

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> service.checkAllowed("192.0.2.1")
        );

        assertEquals(429, exception.getStatusCode().value());
        verify(repository).incrementWindowCount(anyString(), any(), any());
    }

    @Test
    void loginLimitAllowsMoreRequestsThanRegistrationLimit() {
        RateLimitRepository repository = mock(RateLimitRepository.class);
        HashService hashService = mock(HashService.class);
        when(hashService.hashRateLimitKey("login-ip:192.0.2.1")).thenReturn("hashed-address");
        when(repository.findRequestCount("hashed-address")).thenReturn(11);
        RateLimitService service = new RateLimitService(repository, hashService, "", 10, 100);

        service.checkLoginAllowed("login-ip:192.0.2.1", "Too many login attempts");
    }

    @Test
    void rejectsLoginRequestsAboveTheLoginLimit() {
        RateLimitRepository repository = mock(RateLimitRepository.class);
        HashService hashService = mock(HashService.class);
        when(hashService.hashRateLimitKey("login-ip:192.0.2.1")).thenReturn("hashed-address");
        when(repository.findRequestCount("hashed-address")).thenReturn(101);
        RateLimitService service = new RateLimitService(repository, hashService, "", 10, 100);

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> service.checkLoginAllowed("login-ip:192.0.2.1", "Too many login attempts")
        );

        assertEquals(429, exception.getStatusCode().value());
    }

    @Test
    void usesCallerSpecificMessageWhenRateLimitIsExceeded() {
        RateLimitRepository repository = mock(RateLimitRepository.class);
        HashService hashService = mock(HashService.class);
        when(hashService.hashRateLimitKey("login-ip:192.0.2.1")).thenReturn("hashed-address");
        when(repository.findRequestCount("hashed-address")).thenReturn(11);
        RateLimitService service = new RateLimitService(repository, hashService, "", 10, 100);

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> service.checkAllowed("login-ip:192.0.2.1", "Too many login attempts")
        );

        assertEquals("Too many login attempts", exception.getReason());
    }

    @Test
    void trustsForwardedAddressOnlyFromConfiguredProxy() {
        RateLimitService service = new RateLimitService(
                mock(RateLimitRepository.class), mock(HashService.class), "10.0.0.5", 10, 100
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

    @Test
    void appliesConfiguredLimitsAndRejectsInvalidConfiguration() {
        RateLimitRepository repository = mock(RateLimitRepository.class);
        HashService hashService = mock(HashService.class);
        when(hashService.hashRateLimitKey("address")).thenReturn("hash");
        when(repository.findRequestCount("hash")).thenReturn(3);
        RateLimitService service = new RateLimitService(repository, hashService, "", 2, 4);
        assertThrows(ResponseStatusException.class, () -> service.checkAllowed("address"));
        service.checkLoginAllowed("address", "Too many login attempts");
        assertThrows(IllegalArgumentException.class,
                () -> new RateLimitService(repository, hashService, "", 0, 4));
    }
}
