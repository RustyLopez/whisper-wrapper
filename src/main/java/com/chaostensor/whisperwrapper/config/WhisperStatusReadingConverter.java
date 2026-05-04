package com.chaostensor.whisperwrapper.config;

import com.chaostensor.whisperwrapper.dto.WhisperStatus;
import io.r2dbc.postgresql.codec.Json;
import tools.jackson.databind.json.JsonMapper;
import org.springframework.core.convert.converter.Converter;
import org.springframework.data.convert.ReadingConverter;
import org.springframework.stereotype.Component;


@ReadingConverter
public class WhisperStatusReadingConverter implements Converter<Json, WhisperStatus> {
    private final JsonMapper jsonMapper;

    public WhisperStatusReadingConverter(JsonMapper jsonMapper) {
        this.jsonMapper = jsonMapper;
    }

    @Override
    public WhisperStatus convert(Json source) {
        return jsonMapper.readValue(source.asString(), WhisperStatus.class);
    }
}