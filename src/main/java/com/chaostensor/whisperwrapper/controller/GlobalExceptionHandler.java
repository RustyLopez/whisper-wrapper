package com.chaostensor.whisperwrapper.controller;


import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import tools.jackson.databind.exc.MismatchedInputException;
import tools.jackson.databind.exc.UnrecognizedPropertyException;

@ControllerAdvice
@Slf4j
public class GlobalExceptionHandler {


    @ExceptionHandler({UnrecognizedPropertyException.class, MismatchedInputException.class})
    public ResponseEntity<ErrorResponse> handleJsonMappingException(MismatchedInputException ex) {
        log.error("JSON mapping error: {}", ex.getMessage(), ex); // Log exception details
        final ErrorResponse error = ErrorResponse.builder().message("JSON mapping failed: " + ex.getMessage()).build();
        return new ResponseEntity<>(error, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler({Exception.class})
    public ResponseEntity<ErrorResponse> handleException(Exception ex) {
        log.error("error: {}", ex.getMessage(), ex); // Log exception details
        final ErrorResponse error = ErrorResponse.builder().message("err: " + ex.getMessage()).build();
        return new ResponseEntity<>(error, HttpStatus.INTERNAL_SERVER_ERROR);
    }
}
