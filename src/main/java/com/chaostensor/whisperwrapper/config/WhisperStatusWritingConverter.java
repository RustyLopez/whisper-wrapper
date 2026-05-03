package com.chaostensor.whisperwrapper.config;

import com.chaostensor.whisperwrapper.dto.WhisperStatus;
import tools.jackson.core.JsonProcessingException;
import tools.jackson.databind.json.JsonMapper;
import org.springframework.core.convert.converter.Converter;
import org.springframework.data.convert.WritingConverter;
import org.springframework.stereotype.Component;

@Component
@WritingConverter
public class WhisperStatusWritingConverter implements Converter<WhisperStatus, String> {
    private final JsonMapper jsonMapper;

    public WhisperStatusWritingConverter(JsonMapper jsonMapper) {
        this.jsonMapper = jsonMapper;
    }

    @Override
    public String convert(WhisperStatus source) {
        try {
            return jsonMapper.writeValueAsString(source);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize WhisperStatus", e);
        }
    }
}