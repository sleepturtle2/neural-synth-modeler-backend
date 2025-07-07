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
    Optional<byte[]> retrieveAudio(String audioRef);
    void deleteAudio(String audioRef);
} 