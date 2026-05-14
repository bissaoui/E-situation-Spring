package com.example.situation.security;

import org.springframework.stereotype.Component;

@Component
public class FieldEncryptionServiceHolder {

    private static volatile FieldEncryptionService instance;

    public FieldEncryptionServiceHolder(FieldEncryptionService service) {
        instance = service;
    }

    static FieldEncryptionService getInstance() {
        FieldEncryptionService service = instance;
        if (service == null) {
            throw new IllegalStateException("FieldEncryptionService is not initialized.");
        }
        return service;
    }
}
