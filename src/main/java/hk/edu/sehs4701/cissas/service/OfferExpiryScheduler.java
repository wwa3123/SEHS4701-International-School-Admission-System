package hk.edu.sehs4701.cissas.service;

import hk.edu.sehs4701.cissas.entity.Application;
import hk.edu.sehs4701.cissas.repository.ApplicationRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Slice;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneId;

@Slf4j
@Component
public class OfferExpiryScheduler {
    private final ApplicationRepository applications;
    private final ApplicationResultService results;

    @Autowired
    public OfferExpiryScheduler(ApplicationRepository applications, ApplicationResultService results) {
        this.applications = applications;
        this.results = results;
    }

    @Scheduled(fixedDelay = 60_000)
    public void rejectExpiredOffers() {
        LocalDateTime deadline = LocalDateTime.now(ZoneId.of("Asia/Hong_Kong"));
        Long afterId = null;
        Slice<Application> page;
        do {
            page = applications.findExpiredOffers(deadline, afterId, PageRequest.of(0, 100));
            for (var application : page) {
                try {
                    results.expireOffer(application.getApplicationNumber());
                } catch (RuntimeException exception) {
                    log.error("Failed to expire application {}", application.getApplicationNumber(), exception);
                }
            }
            if (!page.isEmpty()) afterId = page.getContent().getLast().getId();
        } while (page.hasNext());
    }
}
