package com.chaostensor.whisperwrapper.dto;

import lombok.Value;

@Value
public class PendingStatus implements WhisperStatus {
    String status;
}