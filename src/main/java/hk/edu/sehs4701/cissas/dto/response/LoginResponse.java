package hk.edu.sehs4701.cissas.dto.response;

import hk.edu.sehs4701.cissas.enums.ActorRole;

public record LoginResponse(Long id, String email, String firstName, String lastName, ActorRole role,
                            boolean passwordChangeRequired) {
}
