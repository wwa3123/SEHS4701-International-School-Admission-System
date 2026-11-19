package hk.edu.sehs4701.cissas.service;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;

final class MemberPasswordPolicy {
    private MemberPasswordPolicy() {}

    static void validate(String password, String email) {
        if (password == null || password.codePointCount(0, password.length()) < 8
                || password.codePoints().noneMatch(Character::isLetter)
                || password.codePoints().noneMatch(Character::isDigit)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Password must be at least 8 characters and contain a letter and a digit");
        }
        if (password.equalsIgnoreCase(email)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Password must be different from your email address");
        }
        if (password.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Password exceeds the 72-byte limit");
        }
    }
}
