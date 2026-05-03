package com.chaostensor.whisperwrapper.dto;


import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;
import org.springframework.web.multipart.MultipartFile;

@Value
@Builder
@Jacksonized
public class WhisperUploadRequest {

    /**
     * The uploaded video file
     */
    MultipartFile file;

    /**
     * Task to perform: transcribe or translate to another language. Default: "transcribe"
     */
    String task;

    /**
     * Language of the input audio. Default: null (Whisper auto-detects the language)
     */
    String language;

    /**
     * Whisper supports both chunked as well as word level timestamps. Default: "chunk"
     */
    String timestamp;

    /**
     * Specifies the exact number of speakers present in the audio file. Must be at least 1. Cannot be used together with minSpeakers or maxSpeakers.
     */
    Integer numSpeakers;

    /**
     * Sets the minimum number of speakers that the system should consider during diarization. Must be at least 1. Cannot be used together with numSpeakers.
     */
    Integer minSpeakers;

    /**
     * Defines the maximum number of speakers that the system should consider in diarization. Must be at least 1. Cannot be used together with numSpeakers.
     */
    Integer maxSpeakers;
}