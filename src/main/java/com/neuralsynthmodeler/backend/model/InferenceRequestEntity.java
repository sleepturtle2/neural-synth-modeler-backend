package com.neuralsynthmodeler.backend.model;

import java.time.Instant;

public class InferenceRequestEntity {
    private String id;
    private String model;
    private String synth;
    private String status;
    private Instant createdAt;
    private Instant updatedAt;
    private String audioRef;
    private int audioSizeGzipped;
    private int audioSizeUncompressed;
    private String resultRef;
    private String error;
    private String meta;

    // Default constructor
    public InferenceRequestEntity() {}

    // Builder constructor
    private InferenceRequestEntity(Builder builder) {
        this.id = builder.id;
        this.model = builder.model;
        this.synth = builder.synth;
        this.status = builder.status;
        this.createdAt = builder.createdAt;
        this.updatedAt = builder.updatedAt;
        this.audioRef = builder.audioRef;
        this.audioSizeGzipped = builder.audioSizeGzipped;
        this.audioSizeUncompressed = builder.audioSizeUncompressed;
        this.resultRef = builder.resultRef;
        this.error = builder.error;
        this.meta = builder.meta;
    }

    // Getters
    public String getId() { return id; }
    public String getModel() { return model; }
    public String getSynth() { return synth; }
    public String getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public String getAudioRef() { return audioRef; }
    public int getAudioSizeGzipped() { return audioSizeGzipped; }
    public int getAudioSizeUncompressed() { return audioSizeUncompressed; }
    public String getResultRef() { return resultRef; }
    public String getError() { return error; }
    public String getMeta() { return meta; }

    // Setters
    public void setId(String id) { this.id = id; }
    public void setModel(String model) { this.model = model; }
    public void setSynth(String synth) { this.synth = synth; }
    public void setStatus(String status) { this.status = status; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
    public void setAudioRef(String audioRef) { this.audioRef = audioRef; }
    public void setAudioSizeGzipped(int audioSizeGzipped) { this.audioSizeGzipped = audioSizeGzipped; }
    public void setAudioSizeUncompressed(int audioSizeUncompressed) { this.audioSizeUncompressed = audioSizeUncompressed; }
    public void setResultRef(String resultRef) { this.resultRef = resultRef; }
    public void setError(String error) { this.error = error; }
    public void setMeta(String meta) { this.meta = meta; }

    // Builder
    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String id;
        private String model;
        private String synth;
        private String status;
        private Instant createdAt;
        private Instant updatedAt;
        private String audioRef;
        private int audioSizeGzipped;
        private int audioSizeUncompressed;
        private String resultRef;
        private String error;
        private String meta;

        public Builder id(String id) { this.id = id; return this; }
        public Builder model(String model) { this.model = model; return this; }
        public Builder synth(String synth) { this.synth = synth; return this; }
        public Builder status(String status) { this.status = status; return this; }
        public Builder createdAt(Instant createdAt) { this.createdAt = createdAt; return this; }
        public Builder updatedAt(Instant updatedAt) { this.updatedAt = updatedAt; return this; }
        public Builder audioRef(String audioRef) { this.audioRef = audioRef; return this; }
        public Builder audioSizeGzipped(int audioSizeGzipped) { this.audioSizeGzipped = audioSizeGzipped; return this; }
        public Builder audioSizeUncompressed(int audioSizeUncompressed) { this.audioSizeUncompressed = audioSizeUncompressed; return this; }
        public Builder resultRef(String resultRef) { this.resultRef = resultRef; return this; }
        public Builder error(String error) { this.error = error; return this; }
        public Builder meta(String meta) { this.meta = meta; return this; }

        public InferenceRequestEntity build() {
            return new InferenceRequestEntity(this);
        }
    }
} 