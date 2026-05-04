# WhisperX Wrapper

**⚠️ Work in Progress**: This project is still under active development. While it should be stable enough to function as a Java Spring-based HTTP wrapper for the CLI-based WhisperX application, expect potential changes and improvements.

## Overview

This is a Java Spring Boot WebFlux application that provides a REST API wrapper around WhisperX. It enables asynchronous audio/video transcription with advanced features like diarization, alignment, and speaker identification. The wrapper uses Docker for containerization and integrates with PostgreSQL (with pgvector extension, though that's not yet put to use.) for job persistence and vector storage.

The application processes media files by invoking the WhisperX CLI tool asynchronously, handles job statuses, and outputs transcripts in any format WhisperX supports. It's intended for internal or localhost use only at this stage, can operate assuming a shared volume between the caller and the service for large media files rather than relying on direct uploads. It prevents duplicate processing via SHA-256 hashing and supports GPU acceleration via CUDA or MPS.

## Features

- Asynchronous transcription jobs with status polling
- Support for various WhisperX models and compute types
- Diarization, alignment, and speaker identification
- Duplicate prevention via file hashing
- GPU acceleration support (CUDA/MPS)
- RESTful API with WebFlux for reactive processing
- Persistence with PostgreSQL and pgvector
- Docker containerization

## Prerequisites

- Java 21+
- Maven
- Docker and Docker Compose
- WhisperX CLI will be installed in the container environment via the Dockerfile
- PostgreSQL with pgvector extension

## Quick Start

1. Clone the repository:
   ```bash
   git clone <repository-url>
   cd whisper-x-wrapper
   ```

2. Build the application:
   ```bash
   mvn clean package
   ```

3. Start the full stack with Docker Compose:
   ```bash
   docker-compose up
   ```

The API will be available at `http://localhost:8070/swagger-ui.html`.

## API Usage

The API provides endpoints under `/whispers` for managing transcription jobs.

### Create Job from Existing File

**POST** `/whispers`

Creates a transcription job for an existing media file. The file must be accessible in the `media-input` directory.

- **Content-Type**: `application/json`
- **Request Body**:
  ```json
  {
    "fileName": "path/to/audio.wav",
    "task": "transcribe",
    "language": "en",
    "model": "large-v3",
    "computeType": "float16",
    "diarize": true,
    "numSpeakers": 2,
    "alignModel": "WAV2VEC2_ASR_BASE_960H",
    "vadMethod": "pyannote",
    "temperature": 0.0,
    "beamSize": 5,
    "highlightWords": false,
    "hotwords": "example phrase"
  }
  ```

Returns a job ID for status polling.

### Upload File and Create Job

**POST** `/whispers/upload`

Uploads a media file and creates a transcription job.

- **Content-Type**: `multipart/form-data`
- **Form Data**:
  - `file`: The media file to upload
  - Additional parameters as above (optional)

Returns a job ID.

### Get Job Status

**GET** `/whispers/{jobId}`

Retrieves the status of a specific job.

Returns:
- `PENDING`: Job is being processed
- `COMPLETED`: Job finished, includes transcript path
- `FAILED`: Job failed, includes error details

### List All Jobs

**GET** `/whispers`

Returns a list of all job IDs.

## Request Parameters

The following parameters can be set per request to tune transcription:

- `fileName` (String): Relative path to media file in `media-input` (for POST `/whispers`)
- `task` (String): "transcribe" or "translate" (default: "transcribe")
- `language` (String): Language code (e.g., "en"); auto-detect if not provided
- `timestamp` (String): "chunk" or "sentence" (legacy; affects segment resolution)
- `model` (String): Whisper model (e.g., "small", "large-v3")
- `computeType` (String): "default", "float16", "float32", "int8"
- `diarize` (Boolean): Enable speaker diarization (default: false)
- `numSpeakers`, `minSpeakers`, `maxSpeakers` (Integer): Speaker count constraints
- `alignModel` (String): Phoneme-level ASR model for alignment
- `vadMethod` (String): "pyannote" or "silero"
- `vadOnset`, `vadOffset` (Float): VAD thresholds
- `chunkSize` (Integer): Chunk size for VAD merging
- `diarizeModel` (String): Diarization model (default: "pyannote/speaker-diarization-community-1")
- `temperature` (Float): Sampling temperature
- `beamSize` (Integer): Beam search size
- `highlightWords` (Boolean): Underline words in output
- `hotwords` (String): Hint phrases for recognition
- `outputFormat` (String): Output format (currently hardcoded to "srt")

## Environment Variables

Configure the application using these environment variables (set in `docker-compose.yml` or your environment):

### Paths
- `MEDIA_INPUT`: Base path for input media files (default: "/media-input")
- `TRANSCRIPT_OUTPUT`: Base path for transcript outputs (default: "/transcript-output")
- `VIDEO_OUTPUT`: Base path for processed video files (default: "/video-output")

### Transcription Defaults
- `app.whisperx.default-model`: Default Whisper model (e.g., "small")
- `app.whisperx.batch-size`: Parallel batch size (e.g., 24)
- `app.whisperx.compute-type`: Default compute type (e.g., "float16")
- `app.whisperx.default-align-model`: Default alignment model
- `app.whisperx.default-vad-method`: Default VAD method (e.g., "pyannote")
- `app.whisperx.default-output-format`: Default output format (e.g., "srt")

### Hardware
- `app.whisperx.device`: Inference device ("cpu", "cuda", "mps")
- `app.whisperx.device-index`: GPU device index (for CUDA)
- `app.whisperx.print-progress`: Enable progress printing (Boolean)

## Example Usage

Upload a file and transcribe:
```bash
curl -X POST -F "file=@audio.wav" -F "model=large-v3" -F "diarize=true" http://localhost:8070/whispers/upload
```

Check status:
```bash
curl http://localhost:8070/whispers/<job id>
```

Transcripts are saved in the `transcript-output` directory, and processed videos in `video-output`.

## Notes

- Diarization is disabled by default due to model access restrictions.
- HF tokens are not currently supported and won't be until I can work out a good security solution for those. This means you'll need to run an hf download into the mounted models directory to add new models that require an HF Token.
- Designed for internal, secure environments; no additional ACLs for file access.
- This is being built in combination with a Wiki generator , which will hopefully be following suit very soon.  The goal of that related project is to be able to drop a bunch of videos with notes in a dir and get a wiki out the other side.