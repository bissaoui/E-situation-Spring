package com.example.situation.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Base64;
import org.junit.jupiter.api.Test;

class FieldEncryptionServiceTest {

    private static final String BASE64_KEY = Base64.getEncoder().encodeToString(new byte[32]);

    @Test
    void encryptAndDecryptRoundTrip() {
        FieldEncryptionService service = new FieldEncryptionService(BASE64_KEY, "");

        String encrypted = service.encrypt("Confidentiel");

        assertTrue(encrypted.startsWith(FieldEncryptionService.PREFIX));
        assertNotEquals("Confidentiel", encrypted);
        assertEquals("Confidentiel", service.decrypt(encrypted));
    }

    @Test
    void decryptReturnsLegacyPlaintextUnchanged() {
        FieldEncryptionService service = new FieldEncryptionService(BASE64_KEY, "");

        assertEquals("legacy-value", service.decrypt("legacy-value"));
    }

    @Test
    void blankValuesPassThrough() {
        FieldEncryptionService service = new FieldEncryptionService(BASE64_KEY, "");

        assertEquals("", service.encrypt(""));
        assertEquals("", service.decrypt(""));
    }
}
