package hk.edu.sehs4701.cissas.service;

import hk.edu.sehs4701.cissas.entity.Member;
import hk.edu.sehs4701.cissas.entity.OneTimeCode;
import hk.edu.sehs4701.cissas.enums.OneTimeCodePurpose;
import hk.edu.sehs4701.cissas.repository.OneTimeCodeRepository;
import hk.edu.sehs4701.cissas.security.HashService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;

@Service
public class OtcService {
    private static final int PASSWORD_RESET_EXPIRY_MINUTES = 15;
    private static final int MAX_VERIFICATION_CODES_PER_HOUR = 5;
    private final OneTimeCodeRepository oneTimeCodeRepository;
    private final HashService hashService;
    private final NotificationService notificationService;
    private final SecureRandom secureRandom = new SecureRandom();

    @Value("${cissas.otc.expiry-minutes:15}")
    private int otcExpiryMinutes;

    @Value("${cissas.otc.max-attempts:5}")
    private int otcMaxAttempts;

    @Value("${cissas.otc.resend-cooldown-seconds:60}")
    private int otcResendCooldownSeconds;

    @Autowired
    public OtcService(
            OneTimeCodeRepository oneTimeCodeRepository,
            HashService hashService,
            NotificationService notificationService) {
        this.oneTimeCodeRepository = oneTimeCodeRepository;
        this.hashService = hashService;
        this.notificationService = notificationService;
    }

    private String generateOtc() {
        return String.valueOf(100000 + secureRandom.nextInt(900000));
    }

    @Transactional
    public void issueAndSendOtc(Member member) {
        if (oneTimeCodeRepository.countByMemberIdAndPurposeAndCreatedAtAfter(member.getId(),
                OneTimeCodePurpose.VERIFICATION, LocalDateTime.now().minusHours(1))
                >= MAX_VERIFICATION_CODES_PER_HOUR) {
            throw new VerificationCodeLimitException(
                    "Verification code limit reached. Try again after an hour.");
        }
        String rawSixDigitCode = generateOtc();

        OneTimeCode otc = new OneTimeCode();
        otc.setMember(member);
        otc.setPurpose(OneTimeCodePurpose.VERIFICATION);
        otc.setCodeHash(hashService.hashOtcCode(rawSixDigitCode));
        otc.setExpiresAt(LocalDateTime.now().plusMinutes(otcExpiryMinutes));
        otc.setAttemptCount(0);
        otc.setUsed(false);
        oneTimeCodeRepository.save(otc);

        notificationService.sendVerificationCode(member, rawSixDigitCode, otcExpiryMinutes);
    }

    @Transactional
    public void throttleAndResendOtc(Member member) {
        OneTimeCode latestCode = oneTimeCodeRepository
                .findFirstByMemberIdAndPurposeOrderByCreatedAtDescIdDesc(
                        member.getId(), OneTimeCodePurpose.VERIFICATION)
                .orElse(null);

        if (latestCode != null) {
            long elapsedSeconds = Duration.between(latestCode.getCreatedAt(), LocalDateTime.now()).getSeconds();
            long remainingSeconds = otcResendCooldownSeconds - elapsedSeconds;
            if (remainingSeconds > 0) {
                throw new ResendCooldownException(
                        "Please wait " + remainingSeconds + " seconds before requesting a new code.");
            }

            if (!latestCode.isUsed()) {
                latestCode.setUsed(true);
                oneTimeCodeRepository.save(latestCode);
            }

        }

        issueAndSendOtc(member);
    }

    @Transactional
    public void issuePasswordResetCode(Member member) {
        OneTimeCode previous = oneTimeCodeRepository.findFirstByMemberIdAndPurposeOrderByCreatedAtDescIdDesc(
                member.getId(), OneTimeCodePurpose.PASSWORD_RESET).orElse(null);
        if (previous != null && previous.getCreatedAt().plusSeconds(otcResendCooldownSeconds)
                .isAfter(LocalDateTime.now())) {
            return;
        }
        if (previous != null) {
            previous.setUsed(true);
            oneTimeCodeRepository.save(previous);
        }

        String rawCode = generateOtc();
        OneTimeCode otc = new OneTimeCode();
        otc.setMember(member);
        otc.setPurpose(OneTimeCodePurpose.PASSWORD_RESET);
        otc.setCodeHash(hashService.hashOtcCode(rawCode));
        otc.setExpiresAt(LocalDateTime.now().plusMinutes(PASSWORD_RESET_EXPIRY_MINUTES));
        otc.setAttemptCount(0);
        otc.setUsed(false);
        oneTimeCodeRepository.save(otc);
        notificationService.sendPasswordResetCode(member, rawCode, PASSWORD_RESET_EXPIRY_MINUTES);
    }

    public void verifyPasswordResetCode(Long memberId, String rawSubmittedCode) {
        verifyCode(memberId, rawSubmittedCode, OneTimeCodePurpose.PASSWORD_RESET);
    }

    public static class ResendCooldownException extends IllegalStateException {
        public ResendCooldownException(String message) {
            super(message);
        }
    }

    public static class VerificationCodeLimitException extends IllegalStateException {
        public VerificationCodeLimitException(String message) {
            super(message);
        }
    }

    @Transactional(noRollbackFor = IllegalArgumentException.class)
    public void verifyOtc(Long memberId, String rawSubmittedCode) {
        verifyCode(memberId, rawSubmittedCode, OneTimeCodePurpose.VERIFICATION);
    }

    private void verifyCode(Long memberId, String rawSubmittedCode, OneTimeCodePurpose purpose) {
        OneTimeCode otc = oneTimeCodeRepository
                .findFirstByMemberIdAndPurposeOrderByCreatedAtDescIdDesc(memberId, purpose)
                .orElseThrow(() -> new IllegalArgumentException("No otc found for the member."));

        if (otc.isUsed()) {
            throw new IllegalArgumentException("This otc has already been used.");
        }

        if (!LocalDateTime.now().isBefore(otc.getExpiresAt())) {
            throw new IllegalArgumentException("Verification code has expired.");
        }

        if (otc.getAttemptCount() >= otcMaxAttempts) {
            throw new IllegalArgumentException("Maximum verification attempts exceeded.");
        }

        if (!hashService.verifyOtcCode(rawSubmittedCode, otc.getCodeHash())) {
            otc.setAttemptCount(otc.getAttemptCount() + 1);
            oneTimeCodeRepository.save(otc);
            throw new IllegalArgumentException("Invalid verification code.");
        }

        otc.setUsed(true);
        oneTimeCodeRepository.save(otc);
    }



}
