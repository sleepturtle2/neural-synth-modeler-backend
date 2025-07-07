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



    public Mono<Map<String, Object>> handleInference(byte[] gzippedAudio) {
        String requestId = UUID.randomUUID().toString();
        logger.info("Starting inference for request ID: {}", requestId);
        
        try {
            // Decompress audio to get original size
            byte[] audioData = GzipUtils.decompress(gzippedAudio);
            
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
                    .audioSizeGzipped(gzippedAudio.length)
                    .audioSizeUncompressed(audioData.length)
                    .build();
            
            inferenceRequestRepository.save(entity);
            
            // Set initial status
            requestStatusMap.put(requestId, RequestStatus.PENDING);
            
            // Process asynchronously
            processInferenceAsync(requestId, gzippedAudio);
            
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

    private void processInferenceAsync(String requestId, byte[] gzippedAudio) {
        Mono.fromCallable(() -> {
            try {
                logger.info("Decompressing audio for request ID: {}", requestId);
                requestStatusMap.put(requestId, RequestStatus.PROCESSING);
                
                // Decompress the gzipped audio
                byte[] audioData = GzipUtils.decompress(gzippedAudio);
                logger.info("Decompressed audio size: {} bytes", audioData.length);
                
                return audioData;
            } catch (IOException e) {
                logger.error("Failed to decompress audio for request ID: {}", requestId, e);
                requestStatusMap.put(requestId, RequestStatus.ERROR);
                throw new RuntimeException("Failed to decompress audio", e);
            }
        })
        .flatMap(audioData -> sendToBentoML(requestId, audioData))
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
        
        // Create a proper file resource for multipart upload with correct content type
        org.springframework.core.io.ByteArrayResource audioResource = new org.springframework.core.io.ByteArrayResource(audioData) {
            @Override
            public String getFilename() {
                return "audio.wav";
            }
        };
        
        // Create multipart form data with proper content type
        MultiValueMap<String, Object> parts = new LinkedMultiValueMap<>();
        org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
        headers.setContentType(org.springframework.http.MediaType.parseMediaType("audio/wav"));
        parts.add("audio", new org.springframework.http.HttpEntity<>(audioResource, headers));
        
        String predictUrl = modelServerUrl + "/predict";
        logger.info("Sending request to: {}", predictUrl);
        
        return webClient.post()
                .uri(predictUrl)
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(BodyInserters.fromMultipartData(parts))
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
