package com.chaostensor.whisperwrapper.dto;

import lombok.Value;

import java.util.List;

@Value
public class WhisperCollectionResponse {
    List<String> jobIds;
}