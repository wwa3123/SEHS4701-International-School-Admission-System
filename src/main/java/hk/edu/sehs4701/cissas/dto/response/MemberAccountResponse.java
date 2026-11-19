package hk.edu.sehs4701.cissas.dto.response;

import hk.edu.sehs4701.cissas.enums.AccountStatus;

public record MemberAccountResponse(Long id, String email, String firstName, String lastName,
                                    AccountStatus status) {
}
