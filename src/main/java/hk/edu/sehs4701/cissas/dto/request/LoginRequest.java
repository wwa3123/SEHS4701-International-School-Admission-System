package hk.edu.sehs4701.cissas.dto.request;

import hk.edu.sehs4701.cissas.enums.LoginAccountType;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record LoginRequest(
        @NotBlank @Email @Size(max = 150) String email,
        @NotBlank @Size(max = 256) String password,
        @NotNull LoginAccountType accountType
) {
}
