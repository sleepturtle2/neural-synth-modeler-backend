package com.neuralsynthmodeler.backend.service; 

import java.util.UUID;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Optional;
import java.io.IOException;
import reactor.core.publisher.Mono;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.http.MediaType;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import com.neuralsynthmodeler.backend.util.GzipUtils;
import com.neuralsynthmodeler.backend.util.AudioFormatUtils;
import com.neuralsynthmodeler.backend.util.AudioFormatUtils.AudioMetadata;

import com.neuralsynthmodeler.backend.repository.InferenceRequestRepository;
import com.neuralsynthmodeler.backend.model.InferenceRequestEntity;
import com.neuralsynthmodeler.backend.model.SynthType;

import java.time.Instant;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;

@Service
public class InferenceService {

    private static final Logger logger = LoggerFactory.getLogger(InferenceService.class);
    
    @Value("${model.server.url:http://localhost:3000}")
    private String modelServerUrl;
    
    private final WebClient webClient;
    private final InferenceRequestRepository inferenceRequestRepository;
    private final AudioStorageService audioStorageService;
    private final StatusStreamService statusStreamService;
    private final Map<String, byte[]> resultCache = new ConcurrentHashMap<>();

    @Autowired
    public InferenceService(InferenceRequestRepository inferenceRequestRepository, 
                           AudioStorageService audioStorageService,
                           StatusStreamService statusStreamService) {
        this.inferenceRequestRepository = inferenceRequestRepository;
        this.audioStorageService = audioStorageService;
        this.statusStreamService = statusStreamService;
        this.webClient = WebClient.builder()
                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(50 * 1024 * 1024)) // 50MB
                .build();
    }

    public static enum RequestStatus {
        PENDING, PROCESSING, DONE, ERROR
    }

    public Mono<Map<String, Object>> handleInference(byte[] audioData) {
        String requestId = UUID.randomUUID().toString();
        logger.info("Starting inference for request ID: {}", requestId);
        
        try {
            // Process and validate audio data using centralized method
            AudioMetadata audioMetadata = AudioFormatUtils.processAudioDataWithErrorDetails(audioData);
            
            logger.info("Audio processing completed for request ID: {} - {}", requestId, audioMetadata);
            
            // Store compressed audio in MongoDB (GZIP-compressed WAV format)
            String audioRef = audioStorageService.storeAudio(
                audioMetadata.getCompressedData(), 
                audioMetadata.getCompressedSize(), 
                audioMetadata.getUncompressedSize()
            );
            logger.info("Compressed audio stored in MongoDB with reference: {}, compressed: {} bytes, uncompressed: {} bytes",
                audioRef, audioMetadata.getCompressedSize(), audioMetadata.getUncompressedSize());
            
            // Create and save inference request entity
            InferenceRequestEntity entity = InferenceRequestEntity.builder()
                    .id(requestId)
                    .model("vital")
                    .synth(SynthType.VITAL.getValue())
                    .status("PENDING")
                    .createdAt(Instant.now())
                    .updatedAt(Instant.now())
                    .audioRef(audioRef)
                    .audioSizeGzipped(audioMetadata.getCompressedSize())
                    .audioSizeUncompressed(audioMetadata.getUncompressedSize())
                    .build();
            
            inferenceRequestRepository.save(entity);
            
            // Set initial status using StatusStreamService as single source of truth
            statusStreamService.updateStatus(requestId, RequestStatus.PENDING);
            
            // Process asynchronously with decompressed audio
            processInferenceAsync(requestId, audioMetadata.getDecompressedData());
            
            Map<String, Object> response = new HashMap<>();
            response.put("request_id", requestId);
            response.put("status", "PENDING");
            return Mono.just(response);
        } catch (IOException e) {
            logger.error("Failed to process audio (compression/decompression error) for request ID: {}", requestId, e);
            Map<String, Object> response = new HashMap<>();
            response.put("request_id", requestId);
            response.put("status", "ERROR");
            response.put("error", "Failed to process audio: " + e.getMessage());
            return Mono.just(response);
        } catch (Exception e) {
            logger.error("Failed to process audio (format error) for request ID: {}", requestId, e);
            Map<String, Object> response = new HashMap<>();
            response.put("request_id", requestId);
            response.put("status", "ERROR");
            response.put("error", e.getMessage());
            return Mono.just(response);
        } 
    }

    private void processInferenceAsync(String requestId, byte[] audioData) {
        Mono.fromCallable(() -> {
            try {
                logger.info("Processing audio for request ID: {}", requestId);
                statusStreamService.updateStatus(requestId, RequestStatus.PROCESSING);
                return audioData;
            } catch (Exception e) {
                logger.error("Failed to add request ID: {} to status map ", requestId);
                statusStreamService.updateStatus(requestId, RequestStatus.ERROR);
                throw new RuntimeException("Failed to process audio", e);
            }
        })
        .flatMap(decompressedAudio -> sendToBentoML(requestId, decompressedAudio))
        .subscribe(
            result -> {
                logger.info("Inference completed successfully for request ID: {}", requestId);
                // Get audioRef from the entity
                Optional<InferenceRequestEntity> entityOpt = inferenceRequestRepository.findById(requestId);
                String synthType = entityOpt.map(InferenceRequestEntity::getSynth).orElse("vital");
                String audioRef = entityOpt.map(InferenceRequestEntity::getAudioRef).orElse(null);
                if (audioRef != null) {
                    // Store preset in MongoDB with synth type and audio reference
                    String presetRef = audioStorageService.storePreset(result, synthType, audioRef);
                    logger.info("Preset stored in MongoDB with reference: {} (linked to audio: {})", presetRef, audioRef);
                    // Update MySQL record with result_ref and status
                    updateInferenceResult(requestId, presetRef, RequestStatus.DONE, null);
                    // Keep in cache for immediate access
                    resultCache.put(requestId, result);
                    statusStreamService.updateStatus(requestId, RequestStatus.DONE);
                } else {
                    logger.error("Could not find audioRef for request ID: {}", requestId);
                    updateInferenceResult(requestId, null, RequestStatus.ERROR, "Audio reference not found");
                    statusStreamService.updateStatus(requestId, RequestStatus.ERROR);
                }
            },
            error -> {
                logger.error("Inference failed for request ID: {}", requestId, error);
                updateInferenceResult(requestId, null, RequestStatus.ERROR, error.getMessage());
                statusStreamService.updateStatus(requestId, RequestStatus.ERROR);
            }
        );
    }

    private Mono<byte[]> sendToBentoML(String requestId, byte[] audioData) {
        logger.info("Sending audio to BentoML for request ID: {}", requestId);
        
        String predictUrl = modelServerUrl + "/predict";
        logger.info("Sending request to: {}", predictUrl);
        logger.debug("Audio data size: {} bytes", audioData.length);
        
        // Convert wav audio data to base64 and create JSON payload
        String base64Audio = java.util.Base64.getEncoder().encodeToString(audioData);
        Map<String, Object> jsonPayload = new HashMap<>();
        jsonPayload.put("audio", base64Audio);
        
        logger.info("Base64 audio length: {} characters", base64Audio.length());
        
        return webClient.post()
                .uri(predictUrl)
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .bodyValue(jsonPayload)
                .retrieve()
                .bodyToMono(byte[].class)
                .doOnSuccess(result -> {
                    logger.info("Received response from BentoML for request ID: {}, size: {} bytes", requestId, result.length);
                })
                .doOnError(error -> logger.error("BentoML request failed for request ID: {}", requestId, error));
    }
    

    
    private void updateInferenceResult(String requestId, String resultRef, RequestStatus status, String error) {
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

    public RequestStatus getStatus(String requestId) {
        // Use StatusStreamService as single source of truth
        return statusStreamService.getStatus(requestId);
    }
    
    /**
     * Get a Flux for real-time status updates for a specific request
     */
    public reactor.core.publisher.Flux<RequestStatus> getStatusStream(String requestId) {
        return statusStreamService.getStatusStream(requestId);
    }
    
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
    
    public void clearResult(String requestId) {
        resultCache.remove(requestId);
        statusStreamService.clearStatus(requestId);
    }

    public Optional<InferenceRequestEntity> getRequestEntity(String requestId) {
        return inferenceRequestRepository.findById(requestId);
    }


}

