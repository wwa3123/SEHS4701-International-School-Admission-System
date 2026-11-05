package hk.edu.sehs4701.cissas.security;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HashServiceTest {
    @Test
    void passwordVerificationUsesDummyBcryptWorkWhenHashIsMissing() {
        HashService hashService = new HashService();
        String password = "valid-password";
        String passwordHash = hashService.hashPassword(password);

        assertTrue(hashService.verifyPassword(password, passwordHash));
        assertFalse(hashService.verifyPassword("wrong-password", passwordHash));
        assertFalse(hashService.verifyPassword(password, null));
    }
}
