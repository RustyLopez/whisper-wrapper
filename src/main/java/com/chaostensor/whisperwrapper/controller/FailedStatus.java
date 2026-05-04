package com.chaostensor.whisperwrapper.controller;

import com.chaostensor.whisperwrapper.dto.WhisperStatus;
import com.fasterxml.jackson.annotation.JsonTypeName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Value;
import lombok.experimental.Accessors;
import lombok.extern.jackson.Jacksonized;

@Jacksonized
@Value
@AllArgsConstructor
@Accessors(fluent = true)
@Builder
public class FailedStatus implements WhisperStatus {
}
