package com.neuralsynthmodeler.backend.service; 

import java.util.UUID;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
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
import com.neuralsynthmodeler.backend.repository.InferenceRequestRepository;
import com.neuralsynthmodeler.backend.model.InferenceRequestEntity;
import java.time.Instant;

@Service
public class InferenceService {

    private static final Logger logger = LoggerFactory.getLogger(InferenceService.class);
    
    @Value("${model.server.url:http://localhost:3000}")
    private String modelServerUrl;
    
    private final WebClient webClient;
    private final InferenceRequestRepository inferenceRequestRepository;
    private final AudioStorageService audioStorageService;
    private final Map<String, RequestStatus> requestStatusMap = new ConcurrentHashMap<>();
    private final Map<String, byte[]> resultCache = new ConcurrentHashMap<>();

    @Autowired
    public InferenceService(InferenceRequestRepository inferenceRequestRepository, 
                           AudioStorageService audioStorageService) {
        this.inferenceRequestRepository = inferenceRequestRepository;
        this.audioStorageService = audioStorageService;
        this.webClient = WebClient.builder()
                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(50 * 1024 * 1024)) // 50MB
                .build();
    }

    public static enum RequestStatus {
        PENDING, PROCESSING, DONE, ERROR
    }

    /**
     * Check if the byte array is GZIP compressed by looking at the magic bytes
     * GZIP files start with the magic bytes 0x1f 0x8b
     */
    private boolean isGzipCompressed(byte[] data) {
        return data.length >= 2 && (data[0] & 0xFF) == 0x1f && (data[1] & 0xFF) == 0x8b;
    }



    public Mono<Map<String, Object>> handleInference(byte[] audioData) {
        String requestId = UUID.randomUUID().toString();
        logger.info("Starting inference for request ID: {}", requestId);
        
        try {
            // Check if the data is GZIP compressed by looking at the magic bytes
            byte[] decompressedAudio;
            int originalSize = audioData.length;
            
            if (isGzipCompressed(audioData)) {
                logger.info("Detected GZIP compressed data, decompressing...");
                decompressedAudio = GzipUtils.decompress(audioData);
                logger.info("Decompressed from {} bytes to {} bytes", originalSize, decompressedAudio.length);
            } else {
                logger.info("Detected uncompressed audio data, using as-is");
                decompressedAudio = audioData;
            }
            
            // Store audio in MongoDB
            String audioRef = audioStorageService.storeAudio(audioData);
            
            // Create and save inference request entity
            InferenceRequestEntity entity = InferenceRequestEntity.builder()
                    .id(requestId)
                    .model("vital")
                    .status("PENDING")
                    .createdAt(Instant.now())
                    .updatedAt(Instant.now())
                    .audioRef(audioRef)
                    .audioSizeGzipped(originalSize)
                    .audioSizeUncompressed(decompressedAudio.length)
                    .build();
            
            inferenceRequestRepository.save(entity);
            
            // Set initial status
            requestStatusMap.put(requestId, RequestStatus.PENDING);
            
            // Process asynchronously
            processInferenceAsync(requestId, audioData);
            
            Map<String, Object> response = new HashMap<>();
            response.put("request_id", requestId);
            response.put("status", "PENDING");
            return Mono.just(response);
        } catch (IOException e) {
            logger.error("Failed to decompress audio for request ID: {}", requestId, e);
            Map<String, Object> response = new HashMap<>();
            response.put("request_id", requestId);
            response.put("status", "ERROR");
            response.put("error", "Failed to decompress audio");
            return Mono.just(response);
        }
    }

    private void processInferenceAsync(String requestId, byte[] audioData) {
        Mono.fromCallable(() -> {
            try {
                logger.info("Processing audio for request ID: {}", requestId);
                requestStatusMap.put(requestId, RequestStatus.PROCESSING);
                
                // Check if the data is GZIP compressed and decompress if needed
                byte[] decompressedAudio;
                if (isGzipCompressed(audioData)) {
                    logger.info("Decompressing GZIP audio for request ID: {}", requestId);
                    decompressedAudio = GzipUtils.decompress(audioData);
                    logger.info("Decompressed audio size: {} bytes", decompressedAudio.length);
                } else {
                    logger.info("Using uncompressed audio for request ID: {}", requestId);
                    decompressedAudio = audioData;
                }
                
                return decompressedAudio;
            } catch (IOException e) {
                logger.error("Failed to process audio for request ID: {}", requestId, e);
                requestStatusMap.put(requestId, RequestStatus.ERROR);
                throw new RuntimeException("Failed to process audio", e);
            }
        })
        .flatMap(decompressedAudio -> sendToBentoML(requestId, decompressedAudio))
        .subscribe(
            result -> {
                logger.info("Inference completed successfully for request ID: {}", requestId);
                resultCache.put(requestId, result);
                requestStatusMap.put(requestId, RequestStatus.DONE);
            },
            error -> {
                logger.error("Inference failed for request ID: {}", requestId, error);
                requestStatusMap.put(requestId, RequestStatus.ERROR);
            }
        );
    }

    private Mono<byte[]> sendToBentoML(String requestId, byte[] audioData) {
        logger.info("Sending audio to BentoML for request ID: {}", requestId);
        
        String predictUrl = modelServerUrl + "/predict";
        logger.info("Sending request to: {}", predictUrl);
        
        return webClient.post()
                .uri(predictUrl)
                .contentType(org.springframework.http.MediaType.parseMediaType("audio/wav"))
                .bodyValue(audioData)
                .retrieve()
                .bodyToMono(byte[].class)
                .doOnSuccess(result -> logger.info("Received response from BentoML for request ID: {}, size: {} bytes", requestId, result.length))
                .doOnError(error -> logger.error("BentoML request failed for request ID: {}", requestId, error));
    }

    public RequestStatus getStatus(String requestId) {
        // First check in-memory cache
        RequestStatus status = requestStatusMap.get(requestId);
        if (status != null) {
            return status;
        }
        
        // Fall back to database
        return inferenceRequestRepository.findById(requestId)
                .map(entity -> {
                    switch (entity.getStatus()) {
                        case "PENDING": return RequestStatus.PENDING;
                        case "PROCESSING": return RequestStatus.PROCESSING;
                        case "DONE": return RequestStatus.DONE;
                        case "ERROR": return RequestStatus.ERROR;
                        default: return null;
                    }
                })
                .orElse(null);
    }
    
    public byte[] getResult(String requestId) {
        return resultCache.get(requestId);
    }
    
    public void clearResult(String requestId) {
        resultCache.remove(requestId);
        requestStatusMap.remove(requestId);
    }

    public String processAudioFile(byte[] audioData) {
        // Generate a unique request ID
        String requestId = UUID.randomUUID().toString();
        
        // In a real implementation, you would:
        // 1. Save the audio data to temporary storage
        // 2. Queue the request for processing
        // 3. Return the request ID immediately
        
        // For now, simulate processing by creating a dummy preset
        byte[] dummyPreset = createDummyPreset(audioData);
        resultCache.put(requestId, dummyPreset);
        
        return requestId;
    }

    private byte[] createDummyPreset(byte[] audioData) {
        // Create a dummy .vital preset file
        // In a real implementation, this would be generated by your ML model
        String dummyPresetContent = "// Dummy Vital preset generated from audio\n" +
                                   "// Audio size: " + audioData.length + " bytes\n" +
                                   "// Generated at: " + System.currentTimeMillis() + "\n" +
                                   "{\n" +
                                   "  \"name\": \"Generated Preset\",\n" +
                                   "  \"version\": \"1.0\",\n" +
                                   "  \"parameters\": {}\n" +
                                   "}";
        return dummyPresetContent.getBytes();
    }
}
