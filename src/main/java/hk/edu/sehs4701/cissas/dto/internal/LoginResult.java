package hk.edu.sehs4701.cissas.dto.internal;

import hk.edu.sehs4701.cissas.dto.response.LoginResponse;

public record LoginResult(LoginResponse response, Long credentialVersion) {
}
