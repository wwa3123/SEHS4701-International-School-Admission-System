package hk.edu.sehs4701.cissas.service;

import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.*;

class FailedApplicationEmailRetrySchedulerTest {
    @Test
    void retriesEveryPage() {
        EmailService emails = mock(EmailService.class);
        when(emails.retryUnsentApplicationEmails(0L)).thenReturn(100L);
        when(emails.retryUnsentApplicationEmails(100L)).thenReturn(null);
        when(emails.retryUnsentDuplicateAlerts(0L)).thenReturn(200L);
        when(emails.retryUnsentDuplicateAlerts(200L)).thenReturn(null);

        new FailedApplicationEmailRetryScheduler(emails).retryUnsentEmails();

        verify(emails).retryUnsentApplicationEmails(100L);
        verify(emails).retryUnsentDuplicateAlerts(200L);
    }
}
