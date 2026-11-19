package hk.edu.sehs4701.cissas.service;

import hk.edu.sehs4701.cissas.dto.response.MemberAccountResponse;
import hk.edu.sehs4701.cissas.enums.AccountStatus;
import hk.edu.sehs4701.cissas.repository.MemberRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class MemberAccountService {
    private final MemberRepository members;

    public MemberAccountService(MemberRepository members) {
        this.members = members;
    }

    @Transactional(readOnly = true)
    public Page<MemberAccountResponse> list(Pageable pageable) {
        return members.findAllByOrderByEmailAsc(pageable).map(member -> new MemberAccountResponse(
                member.getId(), member.getEmail(), member.getFirstName(), member.getLastName(),
                member.getAccountStatus()));
    }

    @Transactional
    public void deactivate(Long id) {
        var member = members.findByIdForUpdate(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Member not found"));
        if (member.getAccountStatus() == AccountStatus.SUSPENDED) return;
        member.setAccountStatus(AccountStatus.SUSPENDED);
        member.setCredentialVersion(member.getCredentialVersion() + 1);
        members.save(member);
    }
}
