package com.example.situation.security;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.math.BigDecimal;

@Converter
public class EncryptedBigDecimalAttributeConverter implements AttributeConverter<BigDecimal, String> {

    @Override
    public String convertToDatabaseColumn(BigDecimal attribute) {
        if (attribute == null) {
            return null;
        }
        return FieldEncryptionServiceHolder.getInstance().encrypt(attribute.toPlainString());
    }

    @Override
    public BigDecimal convertToEntityAttribute(String dbData) {
        String plaintext = FieldEncryptionServiceHolder.getInstance().decrypt(dbData);
        if (plaintext == null || plaintext.isBlank()) {
            return null;
        }
        return new BigDecimal(plaintext.trim());
    }
}
