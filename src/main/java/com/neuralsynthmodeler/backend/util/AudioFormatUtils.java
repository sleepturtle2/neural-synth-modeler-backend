package com.neuralsynthmodeler.backend.util;

import java.io.IOException;

/**
 * Utility class for audio format validation and detection
 */
public class AudioFormatUtils {
    
    /**
     * Metadata class for audio processing results
     */
    public static class AudioMetadata {
        private final byte[] compressedData;
        private final byte[] decompressedData;
        private final int compressedSize;
        private final int uncompressedSize;
        private final boolean wasCompressed;
        private final String format;
        
        public AudioMetadata(byte[] compressedData, byte[] decompressedData, 
                           int compressedSize, int uncompressedSize, 
                           boolean wasCompressed, String format) {
            this.compressedData = compressedData;
            this.decompressedData = decompressedData;
            this.compressedSize = compressedSize;
            this.uncompressedSize = uncompressedSize;
            this.wasCompressed = wasCompressed;
            this.format = format;
        }
        
        // Getters
        public byte[] getCompressedData() { return compressedData; }
        public byte[] getDecompressedData() { return decompressedData; }
        public int getCompressedSize() { return compressedSize; }
        public int getUncompressedSize() { return uncompressedSize; }
        public boolean wasCompressed() { return wasCompressed; }
        public String getFormat() { return format; }
        
        @Override
        public String toString() {
            return String.format("AudioMetadata{format='%s', wasCompressed=%s, compressed=%d, uncompressed=%d}", 
                format, wasCompressed, compressedSize, uncompressedSize);
        }
    }

    /**
     * Check if the byte array is a valid WAV file by looking at the WAV header
     * WAV files start with "RIFF" (0x52 0x49 0x46 0x46) followed by "WAVE" (0x57 0x41 0x56 0x45)
     * 
     * @param data The byte array to check
     * @return true if the data appears to be a valid WAV file, false otherwise
     */
    public static boolean isValidWavFormat(byte[] data) {
        if (data.length < 12) {
            return false;
        }
        
        // Check for "RIFF" signature
        boolean hasRiff = (data[0] & 0xFF) == 0x52 && 
                         (data[1] & 0xFF) == 0x49 && 
                         (data[2] & 0xFF) == 0x46 && 
                         (data[3] & 0xFF) == 0x46;
        
        // Check for "WAVE" signature
        boolean hasWave = (data[8] & 0xFF) == 0x57 && 
                         (data[9] & 0xFF) == 0x41 && 
                         (data[10] & 0xFF) == 0x56 && 
                         (data[11] & 0xFF) == 0x45;
        
        return hasRiff && hasWave;
    }
    
    /**
     * Convert a byte array to a hexadecimal string representation
     * Useful for debugging and logging binary data
     * 
     * @param bytes The byte array to convert
     * @param length The number of bytes to convert (from the beginning)
     * @return A hexadecimal string representation of the bytes
     */
    public static String bytesToHex(byte[] bytes, int length) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < Math.min(length, bytes.length); i++) {
            sb.append(String.format("%02x ", bytes[i] & 0xff));
        }
        return sb.toString();
    }
    
    /**
     * Get a human-readable description of the detected audio format
     * 
     * @param data The byte array to analyze
     * @return A string describing the detected format or "Unknown"
     */
    public static String getAudioFormatDescription(byte[] data) {
        if (data == null || data.length == 0) {
            return "Empty data";
        }
        
        if (GzipUtils.isGzipCompressed(data)) {
            return "GZIP compressed data (Apache Commons validated)";
        }
        
        if (isValidWavFormat(data)) {
            return "WAV audio file";
        }
        
        // Check for other common audio formats
        if (data.length >= 4) {
            // Check for MP3 (ID3 tag or MPEG sync)
            if ((data[0] & 0xFF) == 0x49 && (data[1] & 0xFF) == 0x44 && 
                (data[2] & 0xFF) == 0x33) {
                return "MP3 audio file (with ID3 tag)";
            }
            
            // Check for FLAC
            if ((data[0] & 0xFF) == 0x66 && (data[1] & 0xFF) == 0x4C && 
                (data[2] & 0xFF) == 0x61 && (data[3] & 0xFF) == 0x43) {
                return "FLAC audio file";
            }
        }
        
        return "Unknown format";
    }
    
    /**
     * Process and validate audio data, handling compression/decompression and format validation
     * This method centralizes all audio processing logic and returns metadata
     * 
     * @param audioData The input audio data (compressed or uncompressed)
     * @return AudioMetadata containing processed data and information
     * @throws IOException If compression/decompression fails
     * @throws IllegalArgumentException If the audio format is not supported
     */
    public static AudioMetadata processAudioData(byte[] audioData) throws IOException, IllegalArgumentException {
        if (audioData == null || audioData.length == 0) {
            throw new IllegalArgumentException("Audio data is null or empty");
        }
        
        // Check if the input data is GZIP compressed
        byte[] decompressed = GzipUtils.tryDecompress(audioData);
        if (decompressed != null) {
            // Input was GZIP compressed, validate the decompressed data
            if (!isValidWavFormat(decompressed)) {
                throw new IllegalArgumentException("Decompressed data is not a valid WAV file");
            }
            
            // Return metadata for GZIP-compressed WAV
            return new AudioMetadata(
                audioData,           // Original compressed data
                decompressed,        // Decompressed WAV data
                audioData.length,    // Compressed size
                decompressed.length, // Uncompressed size
                true,                // Was compressed
                "GZIP-compressed WAV"
            );
        } else {
            // Input was not GZIP compressed, check if it's valid WAV
            if (isValidWavFormat(audioData)) {
                // Valid uncompressed WAV, compress it for storage
                byte[] compressed = GzipUtils.compress(audioData);
                
                // Return metadata for uncompressed WAV
                return new AudioMetadata(
                    compressed,       // Compressed data for storage
                    audioData,        // Original uncompressed WAV data
                    compressed.length, // Compressed size
                    audioData.length,  // Uncompressed size
                    false,             // Was not compressed
                    "WAV"
                );
            } else {
                // Not GZIP and not WAV - unsupported format
                throw new IllegalArgumentException("Unsupported audio format: File is neither valid GZIP nor valid WAV");
            }
        }
    }
    
    /**
     * Process audio data and return metadata, with detailed error information
     * This is a safer version that provides more context in error messages
     * 
     * @param audioData The input audio data
     * @return AudioMetadata containing processed data and information
     * @throws IOException If compression/decompression fails
     * @throws IllegalArgumentException If the audio format is not supported
     */
    public static AudioMetadata processAudioDataWithErrorDetails(byte[] audioData) throws IOException, IllegalArgumentException {
        try {
            return processAudioData(audioData);
        } catch (IllegalArgumentException e) {
            // Provide more detailed error information
            String formatDescription = getAudioFormatDescription(audioData);
            String errorMessage = String.format("Audio processing failed: %s. Detected format: %s, Data size: %d bytes", 
                e.getMessage(), formatDescription, audioData != null ? audioData.length : 0);
            throw new IllegalArgumentException(errorMessage, e);
        }
    }
} 