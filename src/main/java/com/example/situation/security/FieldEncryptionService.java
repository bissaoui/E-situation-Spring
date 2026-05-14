package com.example.situation.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class FieldEncryptionService {

    static final String PREFIX = "enc:v1:";
    private static final Logger log = LoggerFactory.getLogger(FieldEncryptionService.class);
    private static final int GCM_IV_BYTES = 12;
    private static final int GCM_TAG_BITS = 128;

    private final SecretKey secretKey;
    private final SecureRandom secureRandom = new SecureRandom();

    public FieldEncryptionService(
        @Value("${security.data.encryption.key:}") String configuredKey,
        @Value("${security.jwt.secret:}") String jwtSecret
    ) {
        this.secretKey = new SecretKeySpec(resolveKey(configuredKey, jwtSecret), "AES");
    }

    public String encrypt(String plaintext) {
        if (plaintext == null || plaintext.isBlank()) {
            return plaintext;
        }
        try {
            byte[] iv = new byte[GCM_IV_BYTES];
            secureRandom.nextBytes(iv);

            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, new GCMParameterSpec(GCM_TAG_BITS, iv));
            byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));

            byte[] payload = new byte[iv.length + ciphertext.length];
            System.arraycopy(iv, 0, payload, 0, iv.length);
            System.arraycopy(ciphertext, 0, payload, iv.length, ciphertext.length);
            return PREFIX + Base64.getEncoder().encodeToString(payload);
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to encrypt protected field", ex);
        }
    }

    public String decrypt(String databaseValue) {
        if (databaseValue == null || databaseValue.isBlank() || !isEncrypted(databaseValue)) {
            return databaseValue;
        }
        try {
            byte[] payload = Base64.getDecoder().decode(databaseValue.substring(PREFIX.length()));
            byte[] iv = new byte[GCM_IV_BYTES];
            byte[] ciphertext = new byte[payload.length - GCM_IV_BYTES];

            System.arraycopy(payload, 0, iv, 0, iv.length);
            System.arraycopy(payload, iv.length, ciphertext, 0, ciphertext.length);

            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, secretKey, new GCMParameterSpec(GCM_TAG_BITS, iv));
            byte[] plaintext = cipher.doFinal(ciphertext);
            return new String(plaintext, StandardCharsets.UTF_8);
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to decrypt protected field", ex);
        }
    }

    public boolean isEncrypted(String value) {
        return value != null && value.startsWith(PREFIX);
    }

    private static byte[] resolveKey(String configuredKey, String jwtSecret) {
        if (configuredKey != null && !configuredKey.isBlank()) {
            byte[] decoded = Base64.getDecoder().decode(configuredKey.trim());
            if (decoded.length != 32) {
                throw new IllegalStateException("DATA_ENCRYPTION_KEY must be a base64-encoded 32-byte key.");
            }
            return decoded;
        }
        if (jwtSecret != null && !jwtSecret.isBlank()) {
            log.warn("DATA_ENCRYPTION_KEY not set. Deriving the field-encryption key from JWT_SECRET for compatibility. Configure a dedicated data-encryption key for production.");
            return sha256(("field-encryption-v1:" + jwtSecret.trim()).getBytes(StandardCharsets.UTF_8));
        }
        throw new IllegalStateException("Configure DATA_ENCRYPTION_KEY or JWT_SECRET before enabling field-level encryption.");
    }

    private static byte[] sha256(byte[] input) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(input);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("Unable to derive field-encryption key", ex);
        }
    }
}
