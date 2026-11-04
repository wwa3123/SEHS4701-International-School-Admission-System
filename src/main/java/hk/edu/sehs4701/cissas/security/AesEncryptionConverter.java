package hk.edu.sehs4701.cissas.security;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.codec.Hex;
import org.springframework.security.crypto.encrypt.AesGcmBytesEncryptor;
import org.springframework.security.crypto.encrypt.BytesEncryptor;
import jakarta.annotation.PostConstruct;
import java.nio.charset.StandardCharsets;

@Converter
public class AesEncryptionConverter implements AttributeConverter<String, String> {

    private BytesEncryptor encryptor;

    @Value("${identity-document.encryption.secret}")
    private String secret;

    @Value("${identity-document.encryption.salt}")
    private String salt;

    @PostConstruct
    public void init() {
        this.encryptor = AesGcmBytesEncryptor.withPassword(secret, salt).build();
    }

    @Override
    public String convertToDatabaseColumn(String plainHkid) {

        if (plainHkid == null) {
            return null;
        }

        byte[] rawBytes = plainHkid.getBytes(StandardCharsets.UTF_8);

        byte[] encryptedBytes = encryptor.encrypt(rawBytes);

        return new String(Hex.encode(encryptedBytes));
    }

    @Override
    public String convertToEntityAttribute(String encryptedHkid) {

        if (encryptedHkid == null) {
            return null;
        }

        byte[] decodedHex = Hex.decode(encryptedHkid);

        byte[] decryptedBytes = encryptor.decrypt(decodedHex);

        return new String(decryptedBytes, StandardCharsets.UTF_8);
    }
}
