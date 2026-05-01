package com.chaostensor.whisperwrapper.dto;


import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;

@Jacksonized
@Value
@Builder
public class WhisperRequest {

    /**
     *
     * While this service can be run locally as a standalone.
     *
     *
     * For now this service is intended to run in a docker env or kube cluster, accessed internally only,
     * as a side car next to other services which will delegate speech to text transforms to this service.
     *
     * THe current intent is therefore to host a shared volume accessible to all services to handle the actual
     * file access rather than to post the entire file to this service.
     *
     * Even if running locally without any other consuming services, it would be expected that your media sources
     * be on the mounted disk at the configured base directory that this application will query for files.
     *
     * The base path that should be mounted in the docker image ( or on your local file system if just running
     * this via spring boot) will be prepended to this request param to resolve the file to process.
     *
     * We eventually need to be able to receive a dir full of media files as well and include them all
     * as a batch.
     */
     String pathRelativeSharedVolumeMount;

     /**
      * Device ID for your GPU. Just pass the device number when using CUDA, or "mps" for Macs with Apple Silicon. Default: "0"
      */
     String deviceId;

     /**
      * Path to save the transcription output. Default: "output.json"
      */
     String transcriptPath;

     /**
      * Name of the pretrained model/checkpoint to perform ASR. Default: "openai/whisper-large-v3"
      */
     String modelName;

     /**
      * Task to perform: transcribe or translate to another language. Default: "transcribe"
      */
     String task;

     /**
      * Language of the input audio. Default: null (Whisper auto-detects the language)
      */
     String language;

     /**
      * Number of parallel batches you want to compute. Reduce if you face OOMs. Default: 24
      */
     Integer batchSize;

     /**
      * Use Flash Attention 2. Default: false
      */
     Boolean flash;

     /**
      * Whisper supports both chunked as well as word level timestamps. Default: "chunk"
      */
     String timestamp;

     /**
      * Provide a hf.co/settings/token for Pyannote.audio to diarise the audio clips
      */
     String hfToken;

     /**
      * Name of the pretrained model/checkpoint to perform diarization. Default: "pyannote/speaker-diarization"
      */
     String diarizationModel;

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
