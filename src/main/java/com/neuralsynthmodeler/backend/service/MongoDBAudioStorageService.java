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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;
import java.util.UUID;
import com.neuralsynthmodeler.backend.util.VitalPresetUtils;

@Service
public class MongoDBAudioStorageService implements AudioStorageService {

    private static final Logger logger = LoggerFactory.getLogger(MongoDBAudioStorageService.class);
    
    private final MongoDatabase mongoDatabase;
    private final MongoCollection<Document> audioCollection;
    private final MongoCollection<Document> presetCollection;
    private final MongoCollection<Document> audioPresetLinksCollection;

    @Autowired
    public MongoDBAudioStorageService(MongoDatabase mongoDatabase) {
        this.mongoDatabase = mongoDatabase;
        this.audioCollection = mongoDatabase.getCollection("audio_files");
        this.presetCollection = mongoDatabase.getCollection("preset_files");
        this.audioPresetLinksCollection = mongoDatabase.getCollection("audio_preset_links");
    }

    @Override
    public String storeAudio(byte[] audioData) {
        return storeAudio(audioData, audioData.length, audioData.length);
    }
    
    @Override
    public String storeAudio(byte[] audioData, int compressedSize, int uncompressedSize) {
        String audioRef = UUID.randomUUID().toString();
        
        Document audioDoc = new Document()
                .append("_id", audioRef)
                .append("data", new Binary(audioData))
                .append("compressed_size", compressedSize)
                .append("uncompressed_size", uncompressedSize)
                .append("created_at", System.currentTimeMillis());
        
        try{
            InsertOneResult result = audioCollection.insertOne(audioDoc);
            logger.info("Stored audio in MongoDB - ID: {}, compressed: {} bytes, uncompressed: {} bytes, compression ratio: {:.2f}", 
                audioRef, compressedSize, uncompressedSize, (float)(uncompressedSize / compressedSize));
        } catch (Exception e) {
            logger.error("Failed to store audio in MongoDB - ID: {}, error: {}", audioRef, e.getMessage());
            throw e;
        }
        
        return audioRef;
    }

    @Override
    public Optional<byte[]> retrieveAudio(String audioRef) {
        Document audioDoc = audioCollection.find(Filters.eq("_id", audioRef)).first();
        if (audioDoc != null) {
            Binary binaryData = audioDoc.get("data", Binary.class);
            return Optional.of(binaryData.getData());
        }
        return Optional.empty();
    }

    @Override
    public void deleteAudio(String audioRef) {
        audioCollection.deleteOne(Filters.eq("_id", audioRef));
    }
    
    @Override
    public String storePreset(byte[] presetData, String synthType, String audioRef) {
        String presetRef = UUID.randomUUID().toString();
        long createdAt = System.currentTimeMillis();
        
        // Extract metadata based on synth type
        String presetName = null;
        String author = null;
        String presetStyle = null;
        String presetStyles = null;
        String synthVersion = null;
        
        if ("vital".equalsIgnoreCase(synthType)) {
            Optional<VitalPresetUtils.VitalPresetMetadata> vitalMetadata = VitalPresetUtils.extractMetadata(presetData);
            if (vitalMetadata.isPresent()) {
                VitalPresetUtils.VitalPresetMetadata meta = vitalMetadata.get();
                presetName = meta.getPresetStyle(); // Use preset_style as name
                author = meta.getAuthor();
                presetStyle = meta.getPresetStyle();
                presetStyles = meta.getPresetStyles();
                synthVersion = meta.getSynthVersion();
            }
        }
        
        Document presetDoc = new Document()
                .append("_id", presetRef)
                .append("data", new Binary(presetData))
                .append("synth_type", synthType)
                .append("preset_name", presetName)
                .append("author", author)
                .append("preset_style", presetStyle)
                .append("preset_styles", presetStyles)
                .append("synth_version", synthVersion)
                .append("size", presetData.length)
                .append("created_at", createdAt);
        
        try {
            InsertOneResult result = presetCollection.insertOne(presetDoc);
            logger.info("Stored preset in MongoDB - ID: {}, synth: {}, name: '{}', author: '{}', size: {} bytes", 
                presetRef, synthType, presetName, author, presetData.length);
            return presetRef;
        } catch (Exception e) {
            logger.error("Failed to store preset in MongoDB - ID: {}, error: {}", presetRef, e.getMessage());
            throw e;
        }
    }
    
    @Override
    public Optional<byte[]> retrievePreset(String presetRef) {
        Document presetDoc = presetCollection.find(Filters.eq("_id", presetRef)).first();
        if (presetDoc != null) {
            Binary binaryData = presetDoc.get("data", Binary.class);
            return Optional.of(binaryData.getData());
        }
        return Optional.empty();
    }
    
    @Override
    public Optional<AudioStorageService.PresetMetadata> retrievePresetMetadata(String presetRef) {
        Document presetDoc = presetCollection.find(Filters.eq("_id", presetRef)).first();
        if (presetDoc != null) {
            AudioStorageService.PresetMetadata metadata = new AudioStorageService.PresetMetadata(
                presetRef,
                presetDoc.getString("synth_type"),
                presetDoc.getString("preset_name"),
                presetDoc.getString("author"),
                presetDoc.getString("preset_style"),
                presetDoc.getString("preset_styles"),
                presetDoc.getString("synth_version"),
                presetDoc.getInteger("size", 0),
                presetDoc.getLong("created_at")
            );
            return Optional.of(metadata);
        }
        return Optional.empty();
    }
    
    @Override
    public void deletePreset(String presetRef) {
        presetCollection.deleteOne(Filters.eq("_id", presetRef));
    }
    
    @Override
    public void linkAudioToPreset(String audioRef, String presetRef) {
        Document linkDoc = new Document()
                .append("_id", audioRef) // Use audioRef as the primary key
                .append("audio_ref", audioRef)
                .append("preset_ref", presetRef)
                .append("created_at", System.currentTimeMillis());
        
        try {
            // Use upsert to handle cases where audio might already be linked
            audioPresetLinksCollection.replaceOne(
                Filters.eq("_id", audioRef), 
                linkDoc, 
                new com.mongodb.client.model.ReplaceOptions().upsert(true)
            );
            logger.info("Linked audio {} to preset {}", audioRef, presetRef);
        } catch (Exception e) {
            logger.error("Failed to link audio {} to preset {}: {}", audioRef, presetRef, e.getMessage());
            throw e;
        }
    }
    
    @Override
    public Optional<String> getPresetRefForAudio(String audioRef) {
        Document linkDoc = audioPresetLinksCollection.find(Filters.eq("_id", audioRef)).first();
        if (linkDoc != null) {
            String presetRef = linkDoc.getString("preset_ref");
            return Optional.of(presetRef);
        }
        return Optional.empty();
    }
    
    @Override
    public Optional<String> getAudioRefForPreset(String presetRef) {
        Document linkDoc = audioPresetLinksCollection.find(Filters.eq("preset_ref", presetRef)).first();
        if (linkDoc != null) {
            String audioRef = linkDoc.getString("audio_ref");
            return Optional.of(audioRef);
        }
        return Optional.empty();
    }
} 