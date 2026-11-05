package hk.edu.sehs4701.cissas.service;

import hk.edu.sehs4701.cissas.repository.RateLimitRepository;
import hk.edu.sehs4701.cissas.security.HashService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class RateLimitService {
    private static final int MAX_REQUESTS_PER_MINUTE = Integer.parseInt("${cissas.registration.max-requests-per-minute}");
    private static final int MAX_LOGIN_REQUESTS_PER_MINUTE = Integer.parseInt("${cissas.login.max-requests-per-minute}");

    private final RateLimitRepository rateLimitRepository;
    private final HashService hashService;
    private final Set<String> trustedProxies;

    @Autowired
    public RateLimitService(
            RateLimitRepository rateLimitRepository,
            HashService hashService,
            @Value("${cissas.registration.trusted-proxies:}") String trustedProxies) {
        this.rateLimitRepository = rateLimitRepository;
        this.hashService = hashService;
        this.trustedProxies = Arrays.stream(trustedProxies.split(","))
                .map(String::trim)
                .filter(address -> !address.isEmpty())
                .collect(Collectors.toUnmodifiableSet());
    }

    public String resolveClientAddress(HttpServletRequest request) {
        String remoteAddress = request.getRemoteAddr();
        if (!trustedProxies.contains(remoteAddress)) {
            return remoteAddress;
        }

        String forwardedAddress = request.getHeader("X-Forwarded-For");
        if (forwardedAddress == null || !isIpAddress(forwardedAddress.trim())) {
            return remoteAddress;
        }
        return forwardedAddress.trim();
    }

    @Transactional(noRollbackFor = ResponseStatusException.class)
    public void checkAllowed(String rateLimitKey) {
        checkAllowed(rateLimitKey, MAX_REQUESTS_PER_MINUTE, "Too many registration requests");
    }

    @Transactional(noRollbackFor = ResponseStatusException.class)
    public void checkAllowed(String rateLimitKey, String limitMessage) {
        checkAllowed(rateLimitKey, MAX_REQUESTS_PER_MINUTE, limitMessage);
    }

    @Transactional(noRollbackFor = ResponseStatusException.class)
    public void checkLoginAllowed(String rateLimitKey, String limitMessage) {
        checkAllowed(rateLimitKey, MAX_LOGIN_REQUESTS_PER_MINUTE, limitMessage);
    }

    private void checkAllowed(String rateLimitKey, int maxRequestsPerMinute, String limitMessage) {
        LocalDateTime now = LocalDateTime.now();
        String rateKey = hashService.hashRateLimitKey(rateLimitKey);
        rateLimitRepository.incrementWindowCount(rateKey, now, now.minusMinutes(1));
        if (rateLimitRepository.findRequestCount(rateKey) > maxRequestsPerMinute) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, limitMessage);
        }
    }

    @Scheduled(fixedDelay = 60_000)
    @Transactional
    public void removeExpiredEntries() {
        rateLimitRepository.deleteExpired(LocalDateTime.now().minusMinutes(2));
    }

    private boolean isIpAddress(String address) {
        if (address.matches("[0-9]{1,3}(\\.[0-9]{1,3}){3}")) {
            return Arrays.stream(address.split("\\."))
                    .allMatch(part -> Integer.parseInt(part) <= 255);
        }
        if (!address.matches("[0-9a-fA-F:.]+") || !address.contains(":")) {
            return false;
        }
        try {
            return InetAddress.getByName(address).getAddress().length == 16;
        } catch (UnknownHostException ex) {
            return false;
        }
    }
}
