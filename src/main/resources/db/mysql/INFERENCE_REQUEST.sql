-- MySQL schema for inference_request table (MVP, no analytics)
-- S3 replaced by MongoDB for audio storage

CREATE TABLE INFERENCE_REQUEST (
    id VARCHAR(64) PRIMARY KEY,
    model VARCHAR(32) NOT NULL,
    synth VARCHAR(32) NOT NULL,                -- Synth name (e.g., 'vital')
    status VARCHAR(32) NOT NULL,
    created_at DATETIME(3) NOT NULL,
    updated_at DATETIME(3) NOT NULL,
    audio_ref VARCHAR(64) NOT NULL,            -- MongoDB ObjectId or UUID
    audio_size_compressed INT NOT NULL,        -- Size of compressed audio
    result_ref VARCHAR(64),                    -- Reference to result in MongoDB (optional)
    error TEXT,
    meta JSON                                 -- Optional: extensible metadata
); 