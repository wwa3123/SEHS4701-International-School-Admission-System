package hk.edu.sehs4701.cissas.service;

import hk.edu.sehs4701.cissas.dto.request.EmailDispatchRequest;
import hk.edu.sehs4701.cissas.entity.Member;
import hk.edu.sehs4701.cissas.entity.OneTimeCode;
import hk.edu.sehs4701.cissas.enums.EmailTemplateType;
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
    private final OneTimeCodeRepository oneTimeCodeRepository;
    private final HashService hashService;
    private final EmailService emailService;
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
            EmailService emailService) {
        this.oneTimeCodeRepository = oneTimeCodeRepository;
        this.hashService = hashService;
        this.emailService = emailService;
    }

    private String generateOtc() {
        return String.valueOf(100000 + secureRandom.nextInt(900000));
    }

    @Transactional
    public void issueAndSendOtc(Member member) {
        String rawSixDigitCode = generateOtc();

        OneTimeCode otc = new OneTimeCode();
        otc.setMember(member);
        otc.setCodeHash(hashService.hashOtcCode(rawSixDigitCode));
        otc.setExpiresAt(LocalDateTime.now().plusMinutes(otcExpiryMinutes));
        otc.setAttemptCount(0);
        otc.setUsed(false);
        oneTimeCodeRepository.save(otc);

        String memberName = member.getFirstName() + " " + member.getLastName();
        String applicantName = "N/A";
        String applicationNumber = "N/A (Membership Verification)";
        String actionRequired = "Enter the 6-digit verification code within " + otcExpiryMinutes
                + " minutes to activate your CIS membership account.";
        String body = emailService.formatStandardBody(
                memberName,
                applicantName,
                applicationNumber,
                actionRequired,
                "Your one-time 6-digit verification code is: " + rawSixDigitCode
                        + "\nThis code expires in " + otcExpiryMinutes + " minutes."
        );

        emailService.sendAndLogEmail(new EmailDispatchRequest(
                member,
                null,
                EmailTemplateType.OTC_VERIFICATION,
                applicantName,
                applicationNumber,
                actionRequired,
                "[CIS SAS] Email Verification Code",
                body
        ));
    }

    @Transactional
    public void throttleAndResendOtc(Member member) {
        OneTimeCode latestCode = oneTimeCodeRepository
                .findFirstByMemberIdOrderByCreatedAtDesc(member.getId())
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

    public static class ResendCooldownException extends IllegalStateException {
        public ResendCooldownException(String message) {
            super(message);
        }
    }

    @Transactional(noRollbackFor = IllegalArgumentException.class)
    public void verifyOtc(Long memberId, String rawSubmittedCode) {
        OneTimeCode otc = oneTimeCodeRepository.findFirstByMemberIdOrderByCreatedAtDesc(memberId)
                .orElseThrow(() -> new IllegalArgumentException("No otc found for the member."));

        if (otc.isUsed()) {
            throw new IllegalArgumentException("This otc has already been used.");
        }

        if (LocalDateTime.now().isAfter(otc.getExpiresAt())) {
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
