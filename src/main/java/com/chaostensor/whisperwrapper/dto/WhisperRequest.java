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
}
