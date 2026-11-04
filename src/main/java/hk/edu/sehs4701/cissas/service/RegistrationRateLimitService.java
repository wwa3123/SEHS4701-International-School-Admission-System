package hk.edu.sehs4701.cissas.service;

import hk.edu.sehs4701.cissas.repository.RegistrationRateLimitRepository;
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
public class RegistrationRateLimitService {
    private static final int MAX_REQUESTS_PER_MINUTE = 10;

    private final RegistrationRateLimitRepository rateLimitRepository;
    private final HashService hashService;
    private final Set<String> trustedProxies;

    @Autowired
    public RegistrationRateLimitService(
            RegistrationRateLimitRepository rateLimitRepository,
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
    public void checkAllowed(String sourceAddress) {
        LocalDateTime now = LocalDateTime.now();
        String rateKey = hashService.hashRegistrationSource(sourceAddress);
        rateLimitRepository.incrementWindowCount(rateKey, now, now.minusMinutes(1));
        if (rateLimitRepository.findRequestCount(rateKey) > MAX_REQUESTS_PER_MINUTE) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Too many registration requests");
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
