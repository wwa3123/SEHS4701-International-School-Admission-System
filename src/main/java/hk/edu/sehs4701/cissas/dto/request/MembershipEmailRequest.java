package hk.edu.sehs4701.cissas.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record MembershipEmailRequest(
        @NotBlank @Email @Size(max = 150) String email
) {
}
