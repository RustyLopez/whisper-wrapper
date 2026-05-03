package com.chaostensor.whisperwrapper.config;

import com.chaostensor.whisperwrapper.dto.WhisperStatus;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.convert.converter.Converter;
import org.springframework.data.convert.ReadingConverter;
import org.springframework.stereotype.Component;

@Component
@ReadingConverter
public class WhisperStatusReadingConverter implements Converter<String, WhisperStatus> {
    private final ObjectMapper objectMapper;

    public WhisperStatusReadingConverter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public WhisperStatus convert(String source) {
        try {
            return objectMapper.readValue(source, WhisperStatus.class);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to deserialize WhisperStatus", e);
        }
    }
}