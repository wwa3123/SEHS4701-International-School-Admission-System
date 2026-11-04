package hk.edu.sehs4701.cissas.service;

import hk.edu.sehs4701.cissas.dto.request.MembershipRegistrationRequest;
import hk.edu.sehs4701.cissas.entity.IdentityDocument;
import hk.edu.sehs4701.cissas.entity.Member;
import hk.edu.sehs4701.cissas.enums.AccountStatus;
import hk.edu.sehs4701.cissas.enums.IdentityDocumentType;
import hk.edu.sehs4701.cissas.repository.IdentityDocumentRepository;
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
    private final IdentityDocumentRepository identityDocumentRepository;
    private final HashService hashService;
    private final OtcService otcService;

    @Autowired
    public MembershipRegistrationService(
            MemberRepository memberRepository,
            IdentityDocumentRepository identityDocumentRepository,
            HashService hashService,
            OtcService otcService) {
        this.memberRepository = memberRepository;
        this.identityDocumentRepository = identityDocumentRepository;
        this.hashService = hashService;
        this.otcService = otcService;
    }

    @Transactional
    public void register(MembershipRegistrationRequest request) {
        if (request.password().getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Password exceeds the 72-byte limit");
        }
        String email = request.email().trim().toLowerCase(Locale.ROOT);
        Member member = memberRepository.findByEmailIgnoreCase(email).orElse(null);
        if (member != null) {
            if (member.getAccountStatus() == AccountStatus.UNVERIFIED) {
                otcService.throttleAndResendOtc(member);
            }
            return;
        }

        String documentNumber = request.documentNumber().trim();
        String issuingCountry = request.issuingCountry() == null
                ? null
                : request.issuingCountry().trim().toUpperCase(Locale.ROOT);
        if (request.documentType() == IdentityDocumentType.HKID) {
            documentNumber = documentNumber.toUpperCase(Locale.ROOT);
            if (!documentNumber.matches("^[A-Z]{1,2}[0-9]{6}\\([0-9A]\\)$")) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid HKID format");
            }
            issuingCountry = "HK";
        } else if (issuingCountry == null || !issuingCountry.matches("^[A-Z]{2}$")) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "A two-letter issuing country is required for this document type"
            );
        }

        member = new Member();
        member.setUsername(email);
        member.setEmail(email);
        member.setPasswordHash(hashService.hashPassword(request.password()));
        member.setFirstName(request.firstName().trim());
        member.setLastName(request.lastName().trim());
        member.setPhoneNumber(request.phoneNumber().trim());
        member.setAccountStatus(AccountStatus.UNVERIFIED);

        IdentityDocument identityDocument = new IdentityDocument();
        identityDocument.setDocumentType(request.documentType());
        identityDocument.setDocumentNumber(documentNumber);
        identityDocument.setIssuingCountry(issuingCountry);
        String documentHash = hashService.generateDocumentNumberHash(identityDocument);
        if (identityDocumentRepository.existsByDocumentNumberHash(documentHash)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Identity document is already linked to an account");
        }

        identityDocument.setDocumentNumberHash(documentHash);
        member.addIdentityDocument(identityDocument);

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
        member.setAccountStatus(AccountStatus.ACTIVE);
        member.setEmailVerifiedAt(LocalDateTime.now());
        memberRepository.save(member);
    }

    @Transactional
    public void resendVerification(String email) {
        memberRepository.findByEmailIgnoreCase(email.trim())
                .filter(member -> member.getAccountStatus() == AccountStatus.UNVERIFIED)
                .ifPresent(otcService::throttleAndResendOtc);
    }
}
