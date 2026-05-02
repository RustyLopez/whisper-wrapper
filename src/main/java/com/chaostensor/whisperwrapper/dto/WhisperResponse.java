package com.chaostensor.whisperwrapper.dto;


import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

@Jacksonized
@Value
@Builder
public class WhisperResponse {

    String jobId;
    WhisperStatus status;
}
