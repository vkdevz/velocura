package com.velocura.security.crypto;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * JPA Attribute Converter for automatic column encryption/decryption of PHI fields.
 */
@Converter
@Component
public class EncryptedStringConverter implements AttributeConverter<String, String> {

    private static AesGcmEncryptor staticEncryptor;

    public EncryptedStringConverter() {
        // Fallback default encryptor if instantiated outside Spring Context
        if (staticEncryptor == null) {
            staticEncryptor = new AesGcmEncryptor("VeloCura#Healthcare$SecureKey2026_HIPAA_Enc");
        }
    }

    @Autowired
    public void setEncryptor(AesGcmEncryptor encryptor) {
        EncryptedStringConverter.staticEncryptor = encryptor;
    }

    @Override
    public String convertToDatabaseColumn(String attribute) {
        if (attribute == null) return null;
        return staticEncryptor.encrypt(attribute);
    }

    @Override
    public String convertToEntityAttribute(String dbData) {
        if (dbData == null) return null;
        return staticEncryptor.decrypt(dbData);
    }
}
