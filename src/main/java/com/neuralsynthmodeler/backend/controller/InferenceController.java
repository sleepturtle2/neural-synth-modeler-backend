package com.neuralsynthmodeler.backend.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.*;
import org.springframework.http.MediaType;
import java.io.IOException;
import com.neuralsynthmodeler.backend.util.GzipUtils;
import org.springframework.beans.factory.annotation.Autowired;
import reactor.core.publisher.Mono;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.neuralsynthmodeler.backend.service.InferenceService;
import org.springframework.web.multipart.MultipartFile;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.beans.factory.annotation.Value;
import java.time.Duration;


@RestController
@RequestMapping("/v1")
public class InferenceController {

    private final InferenceService inferenceService;
    private final WebClient webClient;
    private final String pythonServiceUrl;
    private static final String SUPPORTED_MODEL = "vital";
    private static final Logger logger = LoggerFactory.getLogger(InferenceController.class);

    @Autowired
    public InferenceController(InferenceService inferenceService, 
                              WebClient.Builder webClientBuilder,
                              @Value("${model.server.url}") String pythonServiceUrl) {
        this.inferenceService = inferenceService;
        this.pythonServiceUrl = pythonServiceUrl;
        this.webClient = webClientBuilder
            .baseUrl(pythonServiceUrl)
            .build();
    }

    @GetMapping("")
    public Mono<Map<String, Object>> serverMetadata() {
        return Mono.fromSupplier(() -> {
            Map<String, Object> meta = new HashMap<>();
            meta.put("name", "neural-synth-java-backend");
            meta.put("version", "0.0.1");
            meta.put("extensions", Collections.emptyList());
            return meta;
        });
    }

    @GetMapping("/health/live")
    public Mono<Map<String, Boolean>> healthLive() {
        return Mono.just(Collections.singletonMap("live", true));
    }

    @GetMapping("/health/ready")
    public Mono<Map<String, Boolean>> healthReady() {
        return Mono.just(Collections.singletonMap("ready", true));
    }

    @GetMapping("/models/{modelName}")
    public Mono<Map<String, Object>> modelMetadata(@PathVariable String modelName) {
        if (!SUPPORTED_MODEL.equalsIgnoreCase(modelName)) {
            return Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND, "Model not supported"));
        }
        return Mono.fromSupplier(() -> {
            Map<String, Object> meta = new HashMap<>();
            meta.put("name", SUPPORTED_MODEL);
            meta.put("platform", "pytorch_torchscript");
            meta.put("inputs", Collections.emptyList());
            meta.put("outputs", Collections.emptyList());
            return meta;
        });
    }

    @GetMapping("/models/{modelName}/ready")
    public Mono<Map<String, Object>> modelReady(@PathVariable String modelName) {
        if (!SUPPORTED_MODEL.equalsIgnoreCase(modelName)) {
            return Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND, "Model not supported"));
        }
        return Mono.fromSupplier(() -> {
            Map<String, Object> resp = new HashMap<>();
            resp.put("name", SUPPORTED_MODEL);
            resp.put("ready", true);
            return resp;
        });
    }

    @PostMapping(value = "/models/{modelName}/infer", consumes = MediaType.APPLICATION_OCTET_STREAM_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<Map<String, Object>> infer(@PathVariable String modelName, @RequestBody byte[] gzippedAudio) {
        if (!SUPPORTED_MODEL.equalsIgnoreCase(modelName)) {
            logger.warn("Infer request for unsupported model: {}", modelName);
            return Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND, "Model not supported"));
        }
        logger.info("Received infer request for model '{}', gzipped audio size: {} bytes", modelName, gzippedAudio.length);
        return inferenceService.handleInference(gzippedAudio)
            .doOnSubscribe(sub -> logger.info("Started inference flow for request"))
            .doOnSuccess(resp -> logger.info("Inference flow completed for request, response: {}", resp))
            .doOnError(e -> logger.error("Error in inference flow: {}", e.getMessage(), e));
    }

    @GetMapping("/infer-audio/status/{id}")
    public Mono<Map<String, Object>> getStatus(@PathVariable("id") String requestId) {
        return Mono.fromSupplier(() -> {
            InferenceService.RequestStatus status = inferenceService.getStatus(requestId);
            Map<String, Object> resp = new HashMap<>();
            resp.put("request_id", requestId);
            resp.put("status", status != null ? status.name() : "NOT_FOUND");
            return resp;
        });
    }

    @GetMapping("/preset/{id}")
    public Mono<ResponseEntity<byte[]>> getPreset(@PathVariable("id") String requestId) {
        return Mono.fromSupplier(() -> {
            InferenceService.RequestStatus status = inferenceService.getStatus(requestId);
            
            if (status == null) {
                logger.warn("Preset request for unknown request ID: {}", requestId);
                return ResponseEntity.notFound().build();
            }
            
            if (status != InferenceService.RequestStatus.DONE) {
                logger.warn("Preset request for incomplete inference: {} (status: {})", requestId, status);
                return ResponseEntity.status(HttpStatus.ACCEPTED)
                        .body(("Inference not complete. Status: " + status.name()).getBytes());
            }
            
            byte[] presetData = inferenceService.getResult(requestId);
            if (presetData == null) {
                logger.warn("No preset data found for request ID: {}", requestId);
                return ResponseEntity.notFound().build();
            }
            
            logger.info("Serving preset file for request ID: {}, size: {} bytes", requestId, presetData.length);
            
            // Clean up the result after serving
            inferenceService.clearResult(requestId);
            
            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .header("Content-Disposition", "attachment; filename=\"preset_" + requestId + ".vital\"")
                    .body(presetData);
        });
    }

    @GetMapping("/infer-audio/download/{id}")
    public Mono<ResponseEntity<byte[]>> downloadPreset(@PathVariable("id") String requestId) {
        return Mono.fromSupplier(() -> {
            InferenceService.RequestStatus status = inferenceService.getStatus(requestId);
            
            if (status == null) {
                logger.warn("Preset download request for unknown request ID: {}", requestId);
                return ResponseEntity.notFound().build();
            }
            
            if (status != InferenceService.RequestStatus.DONE) {
                logger.warn("Preset download request for incomplete inference: {} (status: {})", requestId, status);
                return ResponseEntity.status(HttpStatus.ACCEPTED)
                        .body(("Inference not complete. Status: " + status.name()).getBytes());
            }
            
            byte[] presetData = inferenceService.getResult(requestId);
            if (presetData == null) {
                logger.warn("No preset data found for request ID: {}", requestId);
                return ResponseEntity.notFound().build();
            }
            
            logger.info("Serving preset file for download request ID: {}, size: {} bytes", requestId, presetData.length);
            
            // Clean up the result after serving
            inferenceService.clearResult(requestId);
            
            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .header("Content-Disposition", "attachment; filename=\"preset_" + requestId + ".vital\"")
                    .body(presetData);
        });
    }

    @PostMapping(value = "/predict", consumes = MediaType.MULTIPART_FORM_DATA_VALUE, produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    public Mono<ResponseEntity<byte[]>> predict(@RequestParam("audio") MultipartFile audioFile) {
        return Mono.fromCallable(() -> {
            try {
                logger.info("Received predict request with audio file: {}, size: {} bytes", 
                    audioFile.getOriginalFilename(), audioFile.getSize());
                
                // Validate file
                if (audioFile.isEmpty()) {
                    logger.warn("Empty audio file received");
                    return ResponseEntity.badRequest().body("Empty audio file".getBytes());
                }
                
                if (!audioFile.getContentType().startsWith("audio/")) {
                    logger.warn("Invalid content type: {}", audioFile.getContentType());
                    return ResponseEntity.badRequest().body("Invalid audio file type".getBytes());
                }
                
                // Get audio data
                byte[] audioData = audioFile.getBytes();
                
                // Call Python backend service to get preset
                logger.info("Calling Python backend service at: {}", pythonServiceUrl);
                byte[] presetData = webClient.post()
                    .uri("/predict")
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .bodyValue(audioData)
                    .retrieve()
                    .bodyToMono(byte[].class)
                    .timeout(Duration.ofMinutes(2)) // 2 minute timeout
                    .block(); // Wait for Python service to return preset
                
                if (presetData == null || presetData.length == 0) {
                    logger.warn("No preset data received from backend model");
                    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                            .body("Failed to generate preset from backend model".getBytes());
                }
                
                // Generate filename with timestamp
                String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss"));
                String filename = "output_" + timestamp + ".vital";
                
                logger.info("Received preset from Python service: {}, size: {} bytes", filename, presetData.length);
                
                return ResponseEntity.ok()
                        .contentType(MediaType.APPLICATION_OCTET_STREAM)
                        .header("Content-Disposition", "attachment; filename=\"" + filename + "\"")
                        .body(presetData);
                        
            } catch (Exception e) {
                logger.error("Error processing audio file: {}", e.getMessage(), e);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body("Error processing audio file".getBytes());
            }
        }).subscribeOn(reactor.core.scheduler.Schedulers.boundedElastic()); // Use bounded elastic for blocking calls
    }
}
