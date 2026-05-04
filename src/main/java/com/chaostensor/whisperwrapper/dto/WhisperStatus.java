package com.chaostensor.whisperwrapper.dto;

import com.chaostensor.whisperwrapper.controller.FailedStatus;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;


@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "status", visible = false)
@JsonSubTypes({
    @JsonSubTypes.Type(value = PendingStatus.class, name = "pending"),
    @JsonSubTypes.Type(value = FailedStatus.class, name = "failed"),
    @JsonSubTypes.Type(value = CompletedStatus.class, name = "completed")
})
public interface WhisperStatus {

}