package com.chaostensor.whisperwrapper.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

/**
 * Implementation of ProcessService that executes commands using Runtime.
 */
@Service
@Slf4j
public class ProcessServiceImpl implements ProcessService {

    @Override
    public Mono<Void> executeCommand(List<String> command) {
        return Mono.fromCallable(() -> {
            final Process process;
            try {
                log.info("Executing command: {}", command);
                process = Runtime.getRuntime().exec(command.toArray(new String[0]));
            } catch (IOException e) {
                throw new RuntimeException("Failed to initialize the process", e);
            }

            final BufferedReader outputReader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            final BufferedReader errorReader = new BufferedReader(new InputStreamReader(process.getErrorStream()));

            // Log output
            outputReader.lines().forEach(System.out::println);

            try {
                // Wait for process to complete with timeout
                process.onExit().get(4, TimeUnit.HOURS);
            } catch (TimeoutException te) {
                try {
                    process.destroy();
                } catch (RuntimeException processTerminateFailure) {
                    te.addSuppressed(processTerminateFailure);
                }
                String errorOutput = errorReader.lines().collect(Collectors.joining("\n"));
                throw new RuntimeException("Process failed to complete in time: " + errorOutput, te);
            } catch (ExecutionException | InterruptedException e) {
                String errorOutput = errorReader.lines().collect(Collectors.joining("\n"));
                throw new RuntimeException("Process error: " + errorOutput, e);
            }

            if (process.exitValue() != 0) {
                String errorOutput = errorReader.lines().collect(Collectors.joining("\n"));
                throw new RuntimeException("Process failed with exit code " + process.exitValue() + ": " + errorOutput);
            }

            return null;
        });
    }
}