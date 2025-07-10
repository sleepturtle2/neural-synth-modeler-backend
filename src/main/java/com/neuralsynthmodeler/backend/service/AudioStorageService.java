package com.neuralsynthmodeler.backend.service;

import com.mongodb.client.MongoDatabase;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Updates;
import com.mongodb.client.result.InsertOneResult;
import com.mongodb.client.result.UpdateResult;
import org.bson.Document;
import org.bson.types.Binary;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public interface AudioStorageService {
    String storeAudio(byte[] audioData);
    String storeAudio(byte[] audioData, int compressedSize, int uncompressedSize);
    Optional<byte[]> retrieveAudio(String audioRef);
    void deleteAudio(String audioRef);
    
    // Audio-Preset relationship methods
    void linkAudioToPreset(String audioRef, String presetRef);
    Optional<String> getPresetRefForAudio(String audioRef);
    Optional<String> getAudioRefForPreset(String presetRef);
    
    // Preset storage methods
    String storePreset(byte[] presetData, String synthType, String audioRef);
    Optional<byte[]> retrievePreset(String presetRef);
    Optional<PresetMetadata> retrievePresetMetadata(String presetRef);
    void deletePreset(String presetRef);
    
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