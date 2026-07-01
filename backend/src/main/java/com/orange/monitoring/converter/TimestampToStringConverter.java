package com.orange.monitoring.converter;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Converter
public class TimestampToStringConverter implements AttributeConverter<String, Timestamp> {

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Override
    public Timestamp convertToDatabaseColumn(String attribute) {
        if (attribute == null) {
            return null;
        }
        try {
            return Timestamp.valueOf(LocalDateTime.parse(attribute, FORMATTER));
        } catch (Exception e) {
            return Timestamp.valueOf(attribute);
        }
    }

    @Override
    public String convertToEntityAttribute(Timestamp dbData) {
        if (dbData == null) {
            return null;
        }
        return dbData.toLocalDateTime().format(FORMATTER);
    }
}
