package com.chaostensor.whisperwrapper.dto;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
@JsonSubTypes({
    @JsonSubTypes.Type(value = PendingStatus.class, name = "pending"),
    @JsonSubTypes.Type(value = CompletedStatus.class, name = "completed")
})
public interface WhisperStatus {
    String getStatus();
}