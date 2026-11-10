package hk.edu.sehs4701.cissas.dto.request;

public record PasswordChangeRequest(String currentPassword, String newPassword) {
}
