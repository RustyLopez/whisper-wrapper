--liquibase formatted sql

--changeset opencode:create-whisper-jobs-table
CREATE TABLE whisper_jobs (
    id UUID PRIMARY KEY NOT NULL,
    hash VARCHAR(64),
    status VARCHAR(20) NOT NULL,
    transcript_text TEXT,
    video_path VARCHAR(500) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

--changeset opencode:update-status-column-to-json
ALTER TABLE whisper_jobs ALTER COLUMN status TYPE json;