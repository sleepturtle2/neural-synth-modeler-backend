package com.neuralsynthmodeler.backend.service;

import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;
import java.util.Optional;
import com.neuralsynthmodeler.backend.repository.InferenceRequestRepository;
import com.neuralsynthmodeler.backend.model.InferenceRequestEntity;
import java.time.Instant;

@Service
public class InferenceResultService {

    private static final Logger logger = LoggerFactory.getLogger(InferenceResultService.class);
    
    private final Map<String, byte[]> resultCache = new ConcurrentHashMap<>();
    private final InferenceRequestRepository inferenceRequestRepository;
    private final AudioStorageService audioStorageService;

    @Autowired
    public InferenceResultService(InferenceRequestRepository inferenceRequestRepository,
                                 AudioStorageService audioStorageService) {
        this.inferenceRequestRepository = inferenceRequestRepository;
        this.audioStorageService = audioStorageService;
    }

    /**
     * Get result from cache or storage
     */
    public byte[] getResult(String requestId) {
        // First check in-memory cache
        byte[] cachedResult = resultCache.get(requestId);
        if (cachedResult != null) {
            return cachedResult;
        }
        
        // If not in cache, try to retrieve from MongoDB
        try {
            Optional<InferenceRequestEntity> entityOpt = inferenceRequestRepository.findById(requestId);
            if (entityOpt.isPresent()) {
                InferenceRequestEntity entity = entityOpt.get();
                String resultRef = entity.getResultRef();
                
                if (resultRef != null) {
                    Optional<byte[]> presetData = audioStorageService.retrievePreset(resultRef);
                    if (presetData.isPresent()) {
                        logger.info("Retrieved preset from MongoDB for request ID: {}, size: {} bytes", 
                            requestId, presetData.get().length);
                        
                        // Also log preset metadata if available
                        Optional<AudioStorageService.PresetMetadata> metadata = audioStorageService.retrievePresetMetadata(resultRef);
                        if (metadata.isPresent()) {
                            logger.info("Preset metadata for request ID {}: {}", requestId, metadata.get());
                        }
                        
                        return presetData.get();
                    } else {
                        logger.warn("Preset not found in MongoDB for result_ref: {}", resultRef);
                    }
                } else {
                    logger.warn("No result_ref found for request ID: {}", requestId);
                }
            } else {
                logger.warn("Inference request not found for ID: {}", requestId);
            }
        } catch (Exception e) {
            logger.error("Error retrieving preset from MongoDB for request ID: {}", requestId, e);
        }
        
        return null;
    }

    /**
     * Store result in cache
     */
    public void cacheResult(String requestId, byte[] result) {
        resultCache.put(requestId, result);
        logger.debug("Cached result for request ID: {}, size: {} bytes", requestId, result.length);
    }

    /**
     * Clear result from cache and status map
     */
    public void clearResult(String requestId) {
        resultCache.remove(requestId);
        logger.debug("Cleared cached result for request ID: {}", requestId);
    }

    /**
     * Update inference result in database
     */
    public void updateInferenceResult(String requestId, String resultRef, InferenceService.RequestStatus status, String error) {
        try {
            // Find existing entity
            Optional<InferenceRequestEntity> existingOpt = inferenceRequestRepository.findById(requestId);
            if (existingOpt.isPresent()) {
                InferenceRequestEntity entity = existingOpt.get();
                entity.setStatus(status.name());
                entity.setUpdatedAt(Instant.now());
                entity.setResultRef(resultRef);
                entity.setError(error);
                
                // Save updated entity
                inferenceRequestRepository.save(entity);
                logger.info("Updated inference result for request ID: {}, status: {}, result_ref: {}", 
                    requestId, status, resultRef);
            } else {
                logger.warn("Could not find inference request with ID: {}", requestId);
            }
        } catch (Exception e) {
            logger.error("Failed to update inference result for request ID: {}", requestId, e);
        }
    }
} 