package com.neuralsynthmodeler.backend.service;

import java.util.Optional;

/**
 * Service interface for audio file storage operations
 */
public interface AudioStorageService {
    
    /**
     * Store audio data with default size (no compression info)
     */
    String storeAudio(byte[] audioData);
    
    /**
     * Store audio data with compression information
     */
    String storeAudio(byte[] audioData, int compressedSize, int uncompressedSize);
    
    /**
     * Retrieve audio data by reference
     */
    Optional<byte[]> retrieveAudio(String audioRef);
    
    /**
     * Delete audio data by reference
     */
    void deleteAudio(String audioRef);
    
    /**
     * Get preset reference for a given audio reference
     */
    Optional<String> getPresetRefForAudio(String audioRef);
    
    // Preset storage methods (temporary - will be moved to separate service)
    String storePreset(byte[] presetData, String synthType, String audioRef);
    Optional<byte[]> retrievePreset(String presetRef);
    Optional<PresetMetadata> retrievePresetMetadata(String presetRef);
    void deletePreset(String presetRef);
    Optional<String> getAudioRefForPreset(String presetRef);
    
    /**
     * Metadata class for preset information
     */
    class PresetMetadata {
        private String presetRef;
        private String synthType;
        private String presetName;
        private String author;
        private String presetStyle;
        private String presetStyles;
        private String synthVersion;
        private int size;
        private long createdAt;
        
        // Constructor
        public PresetMetadata(String presetRef, String synthType, String presetName, 
                            String author, String presetStyle, String presetStyles, 
                            String synthVersion, int size, long createdAt) {
            this.presetRef = presetRef;
            this.synthType = synthType;
            this.presetName = presetName;
            this.author = author;
            this.presetStyle = presetStyle;
            this.presetStyles = presetStyles;
            this.synthVersion = synthVersion;
            this.size = size;
            this.createdAt = createdAt;
        }
        
        // Getters
        public String getPresetRef() { return presetRef; }
        public String getSynthType() { return synthType; }
        public String getPresetName() { return presetName; }
        public String getAuthor() { return author; }
        public String getPresetStyle() { return presetStyle; }
        public String getPresetStyles() { return presetStyles; }
        public String getSynthVersion() { return synthVersion; }
        public int getSize() { return size; }
        public long getCreatedAt() { return createdAt; }
        
        @Override
        public String toString() {
            return String.format("PresetMetadata{ref=%s, synth=%s, name='%s', author='%s', style='%s', size=%d}",
                presetRef, synthType, presetName, author, presetStyle, size);
        }
    }
} 