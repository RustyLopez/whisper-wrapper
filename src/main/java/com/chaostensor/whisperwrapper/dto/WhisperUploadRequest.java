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

    /**
     * Name of the Whisper model to use (e.g., "small", "base", "medium", "large-v2", "large-v3"). Default: "small"
     */
    String model;

    /**
     * Format of the output file. Options: "all", "srt", "vtt", "txt", "tsv", "json", "aud". Default: "all"
     */
    String outputFormat;

    /**
     * Apply diarization to assign speaker labels to each segment/word. Default: false
     *
     * @see {@link #diarizeModel} for why this is disabled by default and probably will be difficult to get enabled.
     */
    Boolean diarize;

    /**
     * Name of phoneme-level ASR model to do alignment. Default: null (auto-selected based on language)
     */
    String alignModel;

    /**
     * VAD method to be used: "pyannote" or "silero". Default: "pyannote"
     */
    String vadMethod;

    /**
     * Onset threshold for VAD (see pyannote.audio), reduce this if speech is not being detected. Default: 0.500
     */
    Float vadOnset;

    /**
     * Offset threshold for VAD (see pyannote.audio), reduce this if speech is not being detected. Default: 0.363
     */
    Float vadOffset;

    /**
     * Chunk size for merging VAD segments. Default: 30, reduce this if the chunk is too long.
     */
    Integer chunkSize;

    /**
     * Name of the speaker diarization model to use. Default: "pyannote/speaker-diarization-community-1"
     *                    NOTE: pyannote/speaker-diarization-community-1 has strict download requirements
     *                    SO... we'll be disabling diarization by default. You'll need the model already
     *                    downloaded or to provide an HF token ( I assume that will work if your account
     *                    has already accepted the privacy invading terms ), to use it.
     *
     *                    I'm not seeing a lot of alts for diarization rn so. It's disabled by default.
     */
    String diarizeModel;

    /**
     * Temperature to use for sampling. Default: 0
     *
     * "Temperature in an AI model is a parameter that controls the randomness of the model's output during text generation."
     */
    Float temperature;

    /**
     * Number of beams in beam search, only applicable when temperature is zero. Default: 5
     */
    Integer beamSize;

    /**
     * Whether to underline each word as it is spoken in srt and vtt output. Default: false
     */
    Boolean highlightWords;

    /**
     * Hotwords/hint phrases to the model (e.g. "WhisperX, PyAnnote, GPU"); improves recognition of rare/technical terms
     */
    String hotwords;
}