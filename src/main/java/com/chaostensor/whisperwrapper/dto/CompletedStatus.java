package com.chaostensor.whisperwrapper.dto;

import lombok.Value;

@Value
public class CompletedStatus implements WhisperStatus {
    String status;
    String transcriptData;
}