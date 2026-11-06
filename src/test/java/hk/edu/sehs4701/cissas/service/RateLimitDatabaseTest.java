package hk.edu.sehs4701.cissas.service;

import hk.edu.sehs4701.cissas.DisposableDatabase;
import hk.edu.sehs4701.cissas.repository.RateLimitRepository;
import hk.edu.sehs4701.cissas.security.HashService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
@EnabledIfEnvironmentVariable(named = "CISSAS_TEST_DATABASE_URL", matches = ".+")
class RateLimitDatabaseTest extends DisposableDatabase {
    private static final String TEST_SOURCE = "rate-limit-database-test";

    @Autowired
    private RateLimitRepository repository;

    @Autowired
    private HashService hashService;

    @Autowired
    private RateLimitService rateLimitService;

    @BeforeEach
    @AfterEach
    void clearRateLimitEntry() {
        repository.deleteById(hashService.hashRateLimitKey(TEST_SOURCE));
    }

    @Test
    void concurrentRequestsCannotExceedThePerAddressLimit() throws Exception {
        int requestCount = 20;
        var executor = Executors.newFixedThreadPool(requestCount);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<Boolean>> requests = new ArrayList<>();
            for (int request = 0; request < requestCount; request++) {
                requests.add(executor.submit(() -> {
                    start.await();
                    try {
                        rateLimitService.checkAllowed(TEST_SOURCE);
                        return true;
                    } catch (ResponseStatusException ex) {
                        return false;
                    }
                }));
            }
            start.countDown();
            int allowed = 0;
            for (Future<Boolean> request : requests) {
                if (request.get()) {
                    allowed++;
                }
            }
            assertEquals(10, allowed);
            assertEquals(20, repository.findRequestCount(hashService.hashRateLimitKey(TEST_SOURCE)));
        } finally {
            executor.shutdownNow();
        }
    }
}
