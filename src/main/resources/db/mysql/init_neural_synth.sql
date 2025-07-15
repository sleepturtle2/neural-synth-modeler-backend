-- SQL initialization script for NEURAL_SYNTH database and users

-- 1. Create the database
CREATE DATABASE IF NOT EXISTS NEURAL_SYNTH;
USE NEURAL_SYNTH;

-- 2. Create the INFERENCE_REQUEST table
CREATE TABLE IF NOT EXISTS INFERENCE_REQUEST (
    id VARCHAR(64) PRIMARY KEY,
    model VARCHAR(32) NOT NULL,
    synth VARCHAR(32) NOT NULL,
    status VARCHAR(32) NOT NULL,
    created_at DATETIME(3) NOT NULL,
    updated_at DATETIME(3) NOT NULL,
    audio_ref VARCHAR(64) NOT NULL,
    audio_size_gzipped INT NOT NULL,
    audio_size_uncompressed INT NOT NULL,
    result_ref VARCHAR(64),
    error TEXT,
    meta JSON
);

-- 3. Create users and grant privileges
CREATE USER IF NOT EXISTS 'readwrite'@'%' IDENTIFIED BY 'readwrite';
GRANT SELECT, INSERT, UPDATE, DELETE ON NEURAL_SYNTH.* TO 'readwrite'@'%';

CREATE USER IF NOT EXISTS 'readonly'@'%' IDENTIFIED BY 'readonly';
GRANT SELECT ON NEURAL_SYNTH.* TO 'readonly'@'%';

FLUSH PRIVILEGES; 