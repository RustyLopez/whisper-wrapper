package com.chaostensor.whisperwrapper.service;

import reactor.core.publisher.Mono;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Service for executing external processes.
 */
public interface ProcessService {

    /**
     * Executes a command as an external process.
     * @param command the command and arguments to execute
     * @return Mono<Void> that completes when the process finishes successfully, or errors if it fails
     */
    Mono<Void> executeCommand(List<String> command, long timeout, TimeUnit unit);
}