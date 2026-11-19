package hk.edu.sehs4701.cissas.service;

import hk.edu.sehs4701.cissas.entity.Member;
import hk.edu.sehs4701.cissas.entity.OneTimeCode;
import hk.edu.sehs4701.cissas.enums.OneTimeCodePurpose;
import hk.edu.sehs4701.cissas.repository.OneTimeCodeRepository;
import hk.edu.sehs4701.cissas.security.HashService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class OtcServiceTest {
    @Test
    void persistsLastFailedAttemptAndRejectsFurtherChecks() {
        OneTimeCodeRepository repository = mock(OneTimeCodeRepository.class);
        HashService hashService = mock(HashService.class);
        NotificationService notificationService = mock(NotificationService.class);
        OtcService service = new OtcService(repository, hashService, notificationService);
        ReflectionTestUtils.setField(service, "otcMaxAttempts", 5);

        Member member = new Member();
        member.setId(1L);
        OneTimeCode otc = new OneTimeCode();
        otc.setMember(member);
        otc.setExpiresAt(LocalDateTime.now().plusMinutes(5));
        otc.setAttemptCount(4);
        when(repository.findFirstByMemberIdAndPurposeOrderByCreatedAtDescIdDesc(
                1L, OneTimeCodePurpose.VERIFICATION)).thenReturn(Optional.of(otc));
        when(hashService.verifyOtcCode("123456", otc.getCodeHash())).thenReturn(false);

        assertThrows(IllegalArgumentException.class, () -> service.verifyOtc(1L, "123456"));
        assertEquals(5, otc.getAttemptCount());
        verify(repository).save(otc);

        assertThrows(IllegalArgumentException.class, () -> service.verifyOtc(1L, "123456"));
        verify(hashService, times(1)).verifyOtcCode("123456", otc.getCodeHash());
    }

    @Test
    void refusesVerificationCodeIssuanceAfterFiveInOneHour() {
        OneTimeCodeRepository repository = mock(OneTimeCodeRepository.class);
        HashService hashService = mock(HashService.class);
        NotificationService notifications = mock(NotificationService.class);
        OtcService service = new OtcService(repository, hashService, notifications);
        Member member = new Member();
        member.setId(4L);
        when(repository.countByMemberIdAndPurposeAndCreatedAtAfter(eq(4L), eq(OneTimeCodePurpose.VERIFICATION), any()))
                .thenReturn(5L);

        OtcService.VerificationCodeLimitException exception = assertThrows(
                OtcService.VerificationCodeLimitException.class, () -> service.issueAndSendOtc(member));

        assertEquals("Verification code limit reached. Try again after an hour.", exception.getMessage());
        verify(repository, never()).save(any());
        verifyNoInteractions(hashService, notifications);
    }

    @Test
    void passwordResetCodesArePurposeScopedAndOneUse() {
        OneTimeCodeRepository repository = mock(OneTimeCodeRepository.class);
        HashService hashService = mock(HashService.class);
        NotificationService notifications = mock(NotificationService.class);
        OtcService service = new OtcService(repository, hashService, notifications);
        Member member = new Member();
        member.setId(1L);
        OneTimeCode code = new OneTimeCode();
        code.setMember(member);
        code.setPurpose(OneTimeCodePurpose.PASSWORD_RESET);
        code.setExpiresAt(LocalDateTime.now().plusMinutes(15));
        code.setAttemptCount(0);
        code.setCodeHash("hash");
        ReflectionTestUtils.setField(service, "otcMaxAttempts", 5);
        when(repository.findFirstByMemberIdAndPurposeOrderByCreatedAtDescIdDesc(
                1L, OneTimeCodePurpose.PASSWORD_RESET)).thenReturn(Optional.of(code));
        when(hashService.verifyOtcCode("123456", "hash")).thenReturn(true);

        service.verifyPasswordResetCode(1L, "123456");

        assertTrue(code.isUsed());
        assertThrows(IllegalArgumentException.class, () -> service.verifyPasswordResetCode(1L, "123456"));
        verify(hashService, times(1)).verifyOtcCode("123456", "hash");
    }

    @Test
    void issuingPasswordResetCodeInvalidatesThePreviousCode() {
        OneTimeCodeRepository repository = mock(OneTimeCodeRepository.class);
        HashService hashService = mock(HashService.class);
        NotificationService notifications = mock(NotificationService.class);
        OtcService service = new OtcService(repository, hashService, notifications);
        Member member = new Member();
        member.setId(7L);
        OneTimeCode previous = new OneTimeCode();
        previous.setCreatedAt(LocalDateTime.now().minusSeconds(61));
        previous.setAttemptCount(0);
        when(repository.findFirstByMemberIdAndPurposeOrderByCreatedAtDescIdDesc(
                7L, OneTimeCodePurpose.PASSWORD_RESET)).thenReturn(Optional.of(previous));
        when(hashService.hashOtcCode(anyString())).thenReturn("new-hash");
        when(repository.save(any(OneTimeCode.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.issuePasswordResetCode(member);

        assertTrue(previous.isUsed());
        ArgumentCaptor<OneTimeCode> saved = ArgumentCaptor.forClass(OneTimeCode.class);
        verify(repository, times(2)).save(saved.capture());
        OneTimeCode issued = saved.getAllValues().get(1);
        assertEquals(OneTimeCodePurpose.PASSWORD_RESET, issued.getPurpose());
        assertEquals("new-hash", issued.getCodeHash());
        assertTrue(issued.getExpiresAt().isAfter(LocalDateTime.now().plusMinutes(14)));
        verify(notifications).sendPasswordResetCode(eq(member), anyString(), eq(15));
    }

    @Test
    void passwordResetRequestDuringCooldownKeepsCurrentCodeAndSendsNoEmail() {
        OneTimeCodeRepository repository = mock(OneTimeCodeRepository.class);
        HashService hashService = mock(HashService.class);
        NotificationService notifications = mock(NotificationService.class);
        OtcService service = new OtcService(repository, hashService, notifications);
        Member member = new Member();
        member.setId(7L);
        ReflectionTestUtils.setField(service, "otcResendCooldownSeconds", 60);
        OneTimeCode current = new OneTimeCode();
        current.setCreatedAt(LocalDateTime.now().minusSeconds(30));
        when(repository.findFirstByMemberIdAndPurposeOrderByCreatedAtDescIdDesc(
                7L, OneTimeCodePurpose.PASSWORD_RESET)).thenReturn(Optional.of(current));

        service.issuePasswordResetCode(member);

        assertFalse(current.isUsed());
        verify(repository, never()).save(any());
        verifyNoInteractions(hashService, notifications);
    }

    @Test
    void passwordResetCodeIsExpiredAtItsDeadline() {
        OneTimeCodeRepository repository = mock(OneTimeCodeRepository.class);
        HashService hashService = mock(HashService.class);
        OtcService service = new OtcService(repository, hashService, mock(NotificationService.class));
        OneTimeCode code = new OneTimeCode();
        code.setExpiresAt(LocalDateTime.now().minusNanos(1));
        code.setAttemptCount(0);
        when(repository.findFirstByMemberIdAndPurposeOrderByCreatedAtDescIdDesc(
                7L, OneTimeCodePurpose.PASSWORD_RESET)).thenReturn(Optional.of(code));

        assertThrows(IllegalArgumentException.class, () -> service.verifyPasswordResetCode(7L, "123456"));

        verifyNoInteractions(hashService);
    }
}
