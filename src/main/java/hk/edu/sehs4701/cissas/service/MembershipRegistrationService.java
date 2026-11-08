package hk.edu.sehs4701.cissas.service;

import hk.edu.sehs4701.cissas.dto.request.MembershipRegistrationRequest;
import hk.edu.sehs4701.cissas.dto.request.MembershipEmailRequest;
import hk.edu.sehs4701.cissas.entity.IdentityDocument;
import hk.edu.sehs4701.cissas.entity.Member;
import hk.edu.sehs4701.cissas.enums.AccountStatus;
import hk.edu.sehs4701.cissas.repository.MemberRepository;
import hk.edu.sehs4701.cissas.security.HashService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Locale;

@Service
public class MembershipRegistrationService {
    private final MemberRepository memberRepository;
    private final IdentityDocumentService identityDocumentService;
    private final HashService hashService;
    private final OtcService otcService;

    @Autowired
    public MembershipRegistrationService(
            MemberRepository memberRepository,
            IdentityDocumentService identityDocumentService,
            HashService hashService,
            OtcService otcService) {
        this.memberRepository = memberRepository;
        this.identityDocumentService = identityDocumentService;
        this.hashService = hashService;
        this.otcService = otcService;
    }

    @Transactional
    public void register(MembershipEmailRequest request) {
        String email = request.email().trim().toLowerCase(Locale.ROOT);
        Member member = memberRepository.findByEmailIgnoreCase(email).orElse(null);
        if (member != null) {
            if (member.getAccountStatus() == AccountStatus.UNVERIFIED) {
                otcService.throttleAndResendOtc(member);
            }
            return;
        }

        member = new Member();
        member.setUsername(email);
        member.setEmail(email);
        member.setAccountStatus(AccountStatus.UNVERIFIED);

        Member savedMember = memberRepository.save(member);
        otcService.issueAndSendOtc(savedMember);
    }

    @Transactional(noRollbackFor = IllegalArgumentException.class)
    public void verifyEmail(String email, String code) {
        Member member = memberRepository.findByEmailIgnoreCase(email.trim())
                .orElseThrow(() -> new IllegalArgumentException("Unable to verify membership"));
        if (member.getAccountStatus() != AccountStatus.UNVERIFIED) {
            throw new IllegalArgumentException("Unable to verify membership");
        }

        otcService.verifyOtc(member.getId(), code);
    }

    @Transactional
    public void completeRegistration(String verifiedEmail, MembershipRegistrationRequest request) {
        String email = request.email().trim().toLowerCase(Locale.ROOT);
        if (!email.equals(verifiedEmail)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Email verification is required");
        }
        if (request.password().getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Password exceeds the 72-byte limit");
        }
        Member member = memberRepository.findByEmailIgnoreCase(email)
                .filter(existing -> existing.getAccountStatus() == AccountStatus.UNVERIFIED)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "Unable to complete registration"));
        if (!member.getIdentityDocuments().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "This pending registration requires administrator assistance");
        }
        IdentityDocument document = identityDocumentService.createIdentityDocument(
                request.documentType(), request.documentNumber(), request.issuingCountry());
        member.setPasswordHash(hashService.hashPassword(request.password()));
        member.setFirstName(request.firstName().trim());
        member.setLastName(request.lastName().trim());
        member.setPhoneNumber(request.phoneNumber().trim());
        member.addIdentityDocument(document);
        member.setEmailVerifiedAt(LocalDateTime.now());
        member.setAccountStatus(AccountStatus.ACTIVE);
    }

    @Transactional
    public void resendVerification(String email) {
        memberRepository.findByEmailIgnoreCase(email.trim())
                .filter(member -> member.getAccountStatus() == AccountStatus.UNVERIFIED)
                .ifPresent(otcService::throttleAndResendOtc);
    }
}
