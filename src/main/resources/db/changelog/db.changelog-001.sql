--liquibase formatted sql

--changeset opencode:create-whisper-jobs-table
CREATE TABLE whisper_jobs (
    id UUID PRIMARY KEY NOT NULL,
    hash VARCHAR(64),
    status jsonb NOT NULL,
    transcript_text TEXT,
    video_path VARCHAR(500) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);