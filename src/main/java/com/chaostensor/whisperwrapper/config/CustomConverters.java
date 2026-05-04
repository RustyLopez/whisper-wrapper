package com.chaostensor.whisperwrapper.config;


import com.chaostensor.whisperwrapper.entity.WhisperJob;
import com.github.f4b6a3.uuid.UuidCreator;
import io.r2dbc.spi.ConnectionFactory;
import lombok.NonNull;
import org.reactivestreams.Publisher;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.data.r2dbc.config.AbstractR2dbcConfiguration;
import org.springframework.data.r2dbc.convert.R2dbcCustomConversions;
import org.springframework.data.r2dbc.dialect.PostgresDialect;
import org.springframework.data.r2dbc.mapping.event.BeforeConvertCallback;
import org.springframework.data.relational.core.sql.SqlIdentifier;
import reactor.core.publisher.Mono;
import tools.jackson.databind.json.JsonMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Configuration
public class CustomConverters {


    @Bean
    public R2dbcCustomConversions customConversions(JsonMapper jsonMapper) {
        List<Converter<?, ?>> converters = new ArrayList<>();
        converters.add(new WhisperStatusReadingConverter(jsonMapper));
        converters.add(new WhisperStatusWritingConverter(jsonMapper));
        return R2dbcCustomConversions.of(PostgresDialect.INSTANCE, converters);
    }

    /**
     * Critical. Gets me every time 0n a new project and really messed with grok.
     *
     * You can't pre-allocate an id.
     *
     * Thing is, you want to generate modern uuids that have a temporal component for index efficiency so we always write
     * to the end of the index, instead of somewhere in the middle of the tree, causing re-balancing.
     *
     * Some modern database scan tank it but the thing is it's not even just uuids. Sometimes you are replicating an id from
     * an external system and you want the id to be pre-allocated.
     *
     * anyway this wouldn't actually sole that latter case.. lol but uh yeh.. solves this one.
     *
     * R2dbc really needs a better pattern here.

     */
    @Bean
    BeforeConvertCallback<WhisperJob> idGeneration() {
        return (entity, table) -> {
            if (entity.getId() == null) {
                entity.setId(UuidCreator.getTimeOrderedEpoch());
            }
            return Mono.just(entity);
        };
    }

}