package com.chaostensor.whisperwrapper.dto;

import com.fasterxml.jackson.annotation.JsonTypeName;
import lombok.AllArgsConstructor;
import lombok.Value;
import lombok.experimental.Accessors;
import lombok.extern.jackson.Jacksonized;

@Jacksonized
@Value
@AllArgsConstructor
@Accessors(fluent = true)
@JsonTypeName("completed")
public class CompletedStatus implements WhisperStatus {
    String status;
    String transcriptData;

    @Override
    public String getStatus() {
        return status;
    }
}