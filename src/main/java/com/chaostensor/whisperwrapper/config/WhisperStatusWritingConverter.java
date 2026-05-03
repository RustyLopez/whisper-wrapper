package com.chaostensor.whisperwrapper.config;

import com.chaostensor.whisperwrapper.dto.WhisperStatus;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.convert.converter.Converter;
import org.springframework.data.convert.WritingConverter;
import org.springframework.stereotype.Component;

@Component
@WritingConverter
public class WhisperStatusWritingConverter implements Converter<WhisperStatus, String> {
    private final ObjectMapper objectMapper;

    public WhisperStatusWritingConverter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public String convert(WhisperStatus source) {
        try {
            return objectMapper.writeValueAsString(source);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize WhisperStatus", e);
        }
    }
}