package com.example.situation.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Base64;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class EncryptedTypedAttributeConvertersTest {

    private static final String BASE64_KEY = Base64.getEncoder().encodeToString(new byte[32]);

    @BeforeAll
    static void initializeHolder() {
        new FieldEncryptionServiceHolder(new FieldEncryptionService(BASE64_KEY, ""));
    }

    @Test
    void bigDecimalConverterEncryptsAndDecrypts() {
        EncryptedBigDecimalAttributeConverter converter = new EncryptedBigDecimalAttributeConverter();

        String encrypted = converter.convertToDatabaseColumn(new BigDecimal("123456.78"));

        assertTrue(encrypted.startsWith(FieldEncryptionService.PREFIX));
        assertEquals(new BigDecimal("123456.78"), converter.convertToEntityAttribute(encrypted));
    }

    @Test
    void bigDecimalConverterAcceptsLegacyPlaintext() {
        EncryptedBigDecimalAttributeConverter converter = new EncryptedBigDecimalAttributeConverter();

        assertEquals(new BigDecimal("42.50"), converter.convertToEntityAttribute("42.50"));
    }

    @Test
    void localDateConverterEncryptsAndDecrypts() {
        EncryptedLocalDateAttributeConverter converter = new EncryptedLocalDateAttributeConverter();

        String encrypted = converter.convertToDatabaseColumn(LocalDate.of(2026, 5, 14));

        assertTrue(encrypted.startsWith(FieldEncryptionService.PREFIX));
        assertEquals(LocalDate.of(2026, 5, 14), converter.convertToEntityAttribute(encrypted));
    }

    @Test
    void localDateConverterAcceptsLegacyPlaintext() {
        EncryptedLocalDateAttributeConverter converter = new EncryptedLocalDateAttributeConverter();

        assertEquals(LocalDate.of(2026, 5, 14), converter.convertToEntityAttribute("2026-05-14"));
    }
}
