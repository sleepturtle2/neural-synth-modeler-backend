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
import java.util.UUID;

@Service
public class MongoDBAudioStorageService implements AudioStorageService {

    private final MongoDatabase mongoDatabase;
    private final MongoCollection<Document> audioCollection;

    @Autowired
    public MongoDBAudioStorageService(MongoDatabase mongoDatabase) {
        this.mongoDatabase = mongoDatabase;
        this.audioCollection = mongoDatabase.getCollection("audio_files");
    }

    @Override
    public String storeAudio(byte[] audioData) {
        String audioRef = UUID.randomUUID().toString();
        
        Document audioDoc = new Document()
                .append("_id", audioRef)
                .append("data", new Binary(audioData))
                .append("size", audioData.length)
                .append("created_at", System.currentTimeMillis());
        
        InsertOneResult result = audioCollection.insertOne(audioDoc);
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
} 