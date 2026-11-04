package hk.edu.sehs4701.cissas.service;

import hk.edu.sehs4701.cissas.entity.Member;
import hk.edu.sehs4701.cissas.entity.OneTimeCode;
import hk.edu.sehs4701.cissas.repository.OneTimeCodeRepository;
import hk.edu.sehs4701.cissas.security.HashService;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

class OtcServiceTest {
    @Test
    void persistsLastFailedAttemptAndRejectsFurtherChecks() {
        OneTimeCodeRepository repository = mock(OneTimeCodeRepository.class);
        HashService hashService = mock(HashService.class);
        EmailService emailService = mock(EmailService.class);
        OtcService service = new OtcService(repository, hashService, emailService);
        ReflectionTestUtils.setField(service, "otcMaxAttempts", 5);

        Member member = new Member();
        member.setId(1L);
        OneTimeCode otc = new OneTimeCode();
        otc.setMember(member);
        otc.setExpiresAt(LocalDateTime.now().plusMinutes(5));
        otc.setAttemptCount(4);
        when(repository.findFirstByMemberIdOrderByCreatedAtDesc(1L)).thenReturn(Optional.of(otc));
        when(hashService.verifyOtcCode("123456", otc.getCodeHash())).thenReturn(false);

        assertThrows(IllegalArgumentException.class, () -> service.verifyOtc(1L, "123456"));
        assertEquals(5, otc.getAttemptCount());
        verify(repository).save(otc);

        assertThrows(IllegalArgumentException.class, () -> service.verifyOtc(1L, "123456"));
        verify(hashService, times(1)).verifyOtcCode("123456", otc.getCodeHash());
    }
}
