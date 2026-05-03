package com.chaostensor.whisperwrapper.controller;

import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;
import org.springframework.http.HttpStatus;

@Value
@Builder
@Jacksonized
public class ErrorResponse {
   String message;
}
