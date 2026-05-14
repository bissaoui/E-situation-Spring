package com.example.situation.security;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.time.LocalDate;

@Converter
public class EncryptedLocalDateAttributeConverter implements AttributeConverter<LocalDate, String> {

    @Override
    public String convertToDatabaseColumn(LocalDate attribute) {
        if (attribute == null) {
            return null;
        }
        return FieldEncryptionServiceHolder.getInstance().encrypt(attribute.toString());
    }

    @Override
    public LocalDate convertToEntityAttribute(String dbData) {
        String plaintext = FieldEncryptionServiceHolder.getInstance().decrypt(dbData);
        if (plaintext == null || plaintext.isBlank()) {
            return null;
        }
        return LocalDate.parse(plaintext.trim());
    }
}
