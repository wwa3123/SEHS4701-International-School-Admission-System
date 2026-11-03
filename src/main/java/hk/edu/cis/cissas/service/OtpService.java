package hk.edu.cis.cissas.service;

import hk.edu.cis.cissas.dto.request.EmailDispatchRequest;
import hk.edu.cis.cissas.entity.Member;
import hk.edu.cis.cissas.entity.OneTimeCode;
import hk.edu.cis.cissas.enums.EmailTemplateType;
import hk.edu.cis.cissas.repository.OneTimeCodeRepository;
import hk.edu.cis.cissas.security.HashService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;

@Service
public class OtpService {
    private final OneTimeCodeRepository oneTimeCodeRepository;
    private final HashService hashService;
    private final EmailService emailService;
    private final SecureRandom secureRandom = new SecureRandom();

    @Value("${cissas.otp.expiry-minutes:15}")
    private int otpExpiryMinutes;

    @Value("${cissas.otp.max-attempts:5}")
    private int otpMaxAttempts;

    @Value("${cissas.otp.resend-cooldown-seconds:60}")
    private int otpResendCooldownSeconds;

    @Autowired
    public OtpService(
            OneTimeCodeRepository oneTimeCodeRepository,
            HashService hashService,
            EmailService emailService) {
        this.oneTimeCodeRepository = oneTimeCodeRepository;
        this.hashService = hashService;
        this.emailService = emailService;
    }

    private String generateOtp() {
        return String.valueOf(100000 + secureRandom.nextInt(900000));
    }

    @Transactional
    public void issueAndSendOtp(Member member) {
        String rawSixDigitCode = generateOtp();

        OneTimeCode otc = new OneTimeCode();
        otc.setMember(member);
        otc.setCodeHash(hashService.hashOtpCode(rawSixDigitCode));
        otc.setExpiresAt(LocalDateTime.now().plusMinutes(otpExpiryMinutes));
        otc.setAttemptCount(0);
        otc.setUsed(false);
        oneTimeCodeRepository.save(otc);

        String memberName = member.getFirstName() + " " + member.getLastName();
        String applicantName = "N/A";
        String applicationNumber = "N/A (Membership Verification)";
        String actionRequired = "Enter the 6-digit verification code within " + otpExpiryMinutes
                + " minutes to activate your CIS membership account.";
        String body = emailService.formatStandardBody(
                memberName,
                applicantName,
                applicationNumber,
                actionRequired,
                "Your one-time 6-digit verification code is: " + rawSixDigitCode
                        + "\nThis code expires in " + otpExpiryMinutes + " minutes."
        );

        emailService.sendAndLogEmail(new EmailDispatchRequest(
                member,
                null,
                EmailTemplateType.OTP_VERIFICATION,
                applicantName,
                applicationNumber,
                actionRequired,
                "[CIS SAS] Email Verification Code",
                body
        ));
    }

    @Transactional
    public void throttleAndResendOtp(Member member) {
        OneTimeCode latestCode = oneTimeCodeRepository
                .findFirstByMemberIdOrderByCreatedAtDesc(member.getId())
                .orElse(null);

        if (latestCode != null) {
            long elapsedSeconds = Duration.between(latestCode.getCreatedAt(), LocalDateTime.now()).getSeconds();
            long remainingSeconds = otpResendCooldownSeconds - elapsedSeconds;
            if (remainingSeconds > 0) {
                throw new IllegalStateException(
                        "Please wait " + remainingSeconds + " seconds before requesting a new code.");
            }

            if (!latestCode.isUsed()) {
                latestCode.setUsed(true);
                oneTimeCodeRepository.save(latestCode);
            }
        }

        issueAndSendOtp(member);
    }

    @Transactional(noRollbackFor = IllegalArgumentException.class)
    public void verifyCode(Long memberId, String rawSubmittedCode) {
        OneTimeCode otc = oneTimeCodeRepository.findFirstByMemberIdOrderByCreatedAtDesc(memberId)
                .orElseThrow(() -> new IllegalArgumentException("No OTP found for the member."));

        if (otc.isUsed()) {
            throw new IllegalArgumentException("This OTP has already been used.");
        }

        if (LocalDateTime.now().isAfter(otc.getExpiresAt())) {
            throw new IllegalArgumentException("Verification code has expired.");
        }

        if (otc.getAttemptCount() >= otpMaxAttempts) {
            throw new IllegalArgumentException("Maximum verification attempts exceeded.");
        }

        if (!hashService.verifyOtpCode(rawSubmittedCode, otc.getCodeHash())) {
            otc.setAttemptCount(otc.getAttemptCount() + 1);
            oneTimeCodeRepository.save(otc);
            throw new IllegalArgumentException("Invalid verification code.");
        }

        otc.setUsed(true);
        oneTimeCodeRepository.save(otc);
    }



}
