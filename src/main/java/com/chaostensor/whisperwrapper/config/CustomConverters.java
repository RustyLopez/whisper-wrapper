package com.chaostensor.whisperwrapper.config;


import io.r2dbc.spi.ConnectionFactory;
import lombok.NonNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.data.r2dbc.config.AbstractR2dbcConfiguration;
import org.springframework.data.r2dbc.convert.R2dbcCustomConversions;
import org.springframework.data.r2dbc.dialect.PostgresDialect;
import tools.jackson.databind.json.JsonMapper;

import java.util.ArrayList;
import java.util.List;

@Configuration
public class CustomConverters {


    @Bean
    public R2dbcCustomConversions customConversions(JsonMapper jsonMapper) {
        List<Converter<?, ?>> converters = new ArrayList<>();
        converters.add(new WhisperStatusReadingConverter(jsonMapper));
        converters.add(new WhisperStatusWritingConverter(jsonMapper));
        return R2dbcCustomConversions.of(PostgresDialect.INSTANCE, converters);
    }

}