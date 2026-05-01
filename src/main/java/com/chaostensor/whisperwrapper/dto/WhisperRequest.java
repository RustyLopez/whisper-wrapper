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
     * as a sidecar next to other services which will delegate speech to text transforms to this service.
     *
     * THe current intent is therefore to host a shared volume accessible to all services to handle the actual
     * file access rather than to post the entire file to this service.
     *
     * This will be relative to the app.media-input path
     *
     *
     * TODO: Security NOTE: this service is currently intended for a wrapper that is intended for local use or  use as
     * an external wrapper within a docker or cluster runtime, isolated from direct use.
     *
     * Which means that we are assuming any reqeust can access any file. But if we ever decide to build out this wrapper
     * lib to run more as a standalone, then we'd want some acls on the files and some concept of ownership before a
     * video file on the server can be submitted for transcription or a transcsript accessed.
     *
     */
     String fileName;

     /**
      * Device ID for your GPU. Just pass the device number when using CUDA, or "mps" for Macs with Apple Silicon. Default: "0"
      */
     // TODO Not likely needed for our use case or something the client would know, or that we would want them to know
     // String deviceId;

     /**
      * Path to save the transcription output. Default: "output.json"
      */
     // not something that should be configurable by the external client as they do not access the results
     // by path and we don't want them able to force our app to write to an arbitrary directory.
     // String transcriptPath;

     /**
      * Name of the pretrained model/checkpoint to perform ASR. Default: "openai/whisper-large-v3"
      */
     // External process should not be able to give us their hf tokens or
     // trigger download of a model we don't already support.
     // TODO see if we can support model selection while still banning
     // automatic download if the model is not already available.
     // String modelName;

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
     // this is something we need to have control over and TODO: tune this for memory constraints in prod and make configurable for local
     // Integer batchSize;

     /**
      * Use Flash Attention 2. Default: false
      */
     // not currently installed in the env. If we can get it installed and there's no reason not to allow this to be used then we'll restore this config param.
     // Boolean flash;

     /**
      * Whisper supports both chunked as well as word level timestamps. Default: "chunk"
      */
     String timestamp;

     /**
      * Provide a hf.co/settings/token for Pyannote.audio to diarise the audio clips
      */
     // External process should not be able to give us their hf tokens or
     // trigger download of a model we don't already support.
     // String hfToken;

     /**
      * Name of the pretrained model/checkpoint to perform diarization. Default: "pyannote/speaker-diarization"
      */
     // External process should not be able to give us their hf tokens or
     // trigger download of a model we don't already support.
     // TODO see if we can support model selection while still banning
     // automatic download if the model is not already available.
     // String diarizationModel;

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
