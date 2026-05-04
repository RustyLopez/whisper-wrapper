package com.chaostensor.whisperwrapper.dto;

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
@JsonTypeName("pending")
@Builder
public class PendingStatus implements WhisperStatus {

}