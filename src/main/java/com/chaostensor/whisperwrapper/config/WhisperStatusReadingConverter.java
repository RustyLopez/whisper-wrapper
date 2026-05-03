package com.chaostensor.whisperwrapper.config;

import com.chaostensor.whisperwrapper.dto.WhisperStatus;
import tools.jackson.databind.json.JsonMapper;
import org.springframework.core.convert.converter.Converter;
import org.springframework.data.convert.ReadingConverter;
import org.springframework.stereotype.Component;


@Component
@ReadingConverter
public class WhisperStatusReadingConverter implements Converter<String, WhisperStatus> {
    private final JsonMapper jsonMapper;

    public WhisperStatusReadingConverter(JsonMapper jsonMapper) {
        this.jsonMapper = jsonMapper;
    }

    @Override
    public WhisperStatus convert(String source) {
        return jsonMapper.readValue(source, WhisperStatus.class);
    }
}