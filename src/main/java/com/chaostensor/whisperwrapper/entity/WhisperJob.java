package com.chaostensor.whisperwrapper.entity;

import com.chaostensor.whisperwrapper.dto.WhisperStatus;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;
import java.util.UUID;

@Table("whisper_jobs")
public class WhisperJob {

    @Id
    private UUID id;

    @Column("hash")
    private String hash;

    @Column("status")
    private WhisperStatus status;

    @Column("transcript_text")
    private String transcriptText;

    @Column("video_path")
    private String videoPath;

    @Column("created_at")
    private LocalDateTime createdAt;

    @Column("updated_at")
    private LocalDateTime updatedAt;

    // Constructors, getters, setters

    public WhisperJob() {
    }

    public WhisperJob(UUID id, String hash, WhisperStatus status, String transcriptText, String videoPath) {
        this.id = id;
        this.hash = hash;
        this.status = status;
        this.transcriptText = transcriptText;
        this.videoPath = videoPath;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    // Getters and setters

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getHash() {
        return hash;
    }

    public void setHash(String hash) {
        this.hash = hash;
    }

    public WhisperStatus getStatus() {
        return status;
    }

    public void setStatus(WhisperStatus status) {
        this.status = status;
        this.updatedAt = LocalDateTime.now();
    }

    public String getTranscriptText() {
        return transcriptText;
    }

    public void setTranscriptText(String transcriptText) {
        this.transcriptText = transcriptText;
        this.updatedAt = LocalDateTime.now();
    }

    public String getVideoPath() {
        return videoPath;
    }

    public void setVideoPath(String videoPath) {
        this.videoPath = videoPath;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}