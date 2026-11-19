package hk.edu.sehs4701.cissas.service;

import hk.edu.sehs4701.cissas.entity.Member;
import hk.edu.sehs4701.cissas.enums.AccountStatus;
import hk.edu.sehs4701.cissas.repository.MemberRepository;
import hk.edu.sehs4701.cissas.security.HashService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

@Service
public class MemberPasswordResetService {
    private final MemberRepository members;
    private final OtcService otcs;
    private final HashService hashes;

    public MemberPasswordResetService(MemberRepository members, OtcService otcs, HashService hashes) {
        this.members = members;
        this.otcs = otcs;
        this.hashes = hashes;
    }

    @Transactional
    public void requestCode(String email) {
        if (email == null || email.isBlank()) return;
        members.findByEmailIgnoreCase(email.trim().toLowerCase(Locale.ROOT))
                .filter(member -> member.getAccountStatus() == AccountStatus.ACTIVE)
                .ifPresent(otcs::issuePasswordResetCode);
    }

    @Transactional(noRollbackFor = IllegalArgumentException.class)
    public void resetPassword(String email, String code, String newPassword) {
        String normalizedEmail = email.trim().toLowerCase(Locale.ROOT);
        MemberPasswordPolicy.validate(newPassword, normalizedEmail);
        Member member = members.findByEmailIgnoreCase(normalizedEmail)
                .filter(existing -> existing.getAccountStatus() == AccountStatus.ACTIVE)
                .orElseThrow(() -> new IllegalArgumentException("Invalid or expired reset code"));
        otcs.verifyPasswordResetCode(member.getId(), code);
        member.setPasswordHash(hashes.hashPassword(newPassword));
        member.setCredentialVersion(member.getCredentialVersion() + 1);
        member.resetLoginFailures();
    }
}
