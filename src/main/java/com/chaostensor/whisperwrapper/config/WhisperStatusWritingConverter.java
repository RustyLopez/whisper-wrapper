package com.chaostensor.whisperwrapper.config;

import com.chaostensor.whisperwrapper.dto.WhisperStatus;
import io.r2dbc.postgresql.codec.Json;
import tools.jackson.databind.json.JsonMapper;
import org.springframework.core.convert.converter.Converter;
import org.springframework.data.convert.WritingConverter;
import org.springframework.stereotype.Component;

@WritingConverter
public class WhisperStatusWritingConverter implements Converter<WhisperStatus, Json> {
    private final JsonMapper jsonMapper;

    public WhisperStatusWritingConverter(JsonMapper jsonMapper) {
        this.jsonMapper = jsonMapper;
    }

    @Override
    public Json convert(WhisperStatus source) {
        return Json.of(jsonMapper.writeValueAsString(source));
    }
}