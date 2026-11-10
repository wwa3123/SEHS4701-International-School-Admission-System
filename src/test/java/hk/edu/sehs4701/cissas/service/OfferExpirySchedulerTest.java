package hk.edu.sehs4701.cissas.service;

import hk.edu.sehs4701.cissas.entity.Application;
import hk.edu.sehs4701.cissas.repository.ApplicationRepository;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.SliceImpl;

import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class OfferExpirySchedulerTest {
    @Test
    void processesLaterBatchesEvenWhenAnOfferFails() {
        ApplicationRepository applications = mock(ApplicationRepository.class);
        ApplicationResultService results = mock(ApplicationResultService.class);
        Application first = new Application();
        first.setId(1L);
        first.setApplicationNumber("APP-1");
        Application second = new Application();
        second.setId(2L);
        second.setApplicationNumber("APP-2");
        when(applications.findExpiredOffers(any(), isNull(), any()))
                .thenReturn(new SliceImpl<>(List.of(first), PageRequest.of(0, 1), true));
        when(applications.findExpiredOffers(any(), eq(1L), any()))
                .thenReturn(new SliceImpl<>(List.of(second)));
        doThrow(new IllegalStateException("test failure")).when(results).expireOffer("APP-1");

        new OfferExpiryScheduler(applications, results).rejectExpiredOffers();

        verify(results).expireOffer("APP-2");
    }
}
