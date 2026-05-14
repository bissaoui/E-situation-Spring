package com.example.situation.security;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter
public class EncryptedStringAttributeConverter implements AttributeConverter<String, String> {

    @Override
    public String convertToDatabaseColumn(String attribute) {
        return FieldEncryptionServiceHolder.getInstance().encrypt(attribute);
    }

    @Override
    public String convertToEntityAttribute(String dbData) {
        return FieldEncryptionServiceHolder.getInstance().decrypt(dbData);
    }
}
