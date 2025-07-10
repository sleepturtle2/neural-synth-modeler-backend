package com.neuralsynthmodeler.backend.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.*;
import org.springframework.http.MediaType;
import java.io.IOException;
import org.springframework.beans.factory.annotation.Autowired;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Flux;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.neuralsynthmodeler.backend.service.InferenceService;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.codec.ServerSentEvent;
import javax.sql.DataSource;
import com.mongodb.client.MongoDatabase;
import com.mongodb.MongoException;


@RestController
@RequestMapping("/v1")
public class InferenceController {

    private final InferenceService inferenceService;
    private final WebClient webClient;
    private final String pythonServiceUrl;
    private static final String SUPPORTED_MODEL = "vital";
    private static final Logger logger = LoggerFactory.getLogger(InferenceController.class);
    private final DataSource dataSource;
    private final MongoDatabase mongoDatabase;

    @Autowired
    public InferenceController(InferenceService inferenceService, 
                              WebClient.Builder webClientBuilder,
                              @Value("${model.server.url}") String pythonServiceUrl,
                              DataSource dataSource,
                              MongoDatabase mongoDatabase) {
        this.inferenceService = inferenceService;
        this.pythonServiceUrl = pythonServiceUrl;
        this.webClient = webClientBuilder
            .baseUrl(pythonServiceUrl)
            .build();
        this.dataSource = dataSource;
        this.mongoDatabase = mongoDatabase;
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

    @GetMapping("/health/ready")
    public Mono<ResponseEntity<Map<String, Object>>> healthReady() {
        Map<String, Object> status = new HashMap<>();
        // Check MySQL
        try (java.sql.Connection conn = dataSource.getConnection()) {
            if (!conn.isValid(2)) {
                status.put("mysql", "Connection is not valid");
            } else {
                status.put("mysql", "ok");
            }
        } catch (Exception e) {
            status.put("mysql", "Error: " + e.getMessage());
        }
        // Check MongoDB
        try {
            mongoDatabase.runCommand(new org.bson.Document("ping", 1));
            status.put("mongo", "ok");
        } catch (Exception e) {
            status.put("mongo", "Error: " + e.getMessage());
        }
        // Check BentoML
        return webClient.get()
            .uri("/healthz")
            .retrieve()
            .bodyToMono(String.class)
            .map(resp -> {
                status.put("bentoml", "ok");
                boolean allOk = status.values().stream().allMatch(v -> "ok".equals(v));
                Map<String, Object> result = new HashMap<>();
                result.put("ready", allOk);
                if (!allOk) result.put("details", status);
                return ResponseEntity.ok(result);
            })
            .onErrorResume(e -> {
                status.put("bentoml", "Error: " + e.getMessage());
                Map<String, Object> result = new HashMap<>();
                result.put("ready", false);
                result.put("details", status);
                return Mono.just(ResponseEntity.status(503).body(result));
            });
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
    public Mono<Map<String, Object>> infer(@PathVariable String modelName, @RequestBody byte[] audioData) {
        if (!SUPPORTED_MODEL.equalsIgnoreCase(modelName)) {
            logger.warn("Infer request for unsupported model: {}", modelName);
            return Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND, "Model not supported"));
        }
        logger.info("Received infer request for model '{}', audio data size: {} bytes", modelName, audioData.length);
        return inferenceService.handleInference(audioData)
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

    @GetMapping(value = "/infer-audio/stream-status/{id}", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<Map<String, Object>>> streamStatus(@PathVariable("id") String requestId) {
        logger.info("Starting SSE stream for request ID: {}", requestId);
        
        return inferenceService.getStatusStream(requestId)
            .map(status -> {
                Map<String, Object> data = new HashMap<>();
                data.put("status", status.name());
                data.put("timestamp", System.currentTimeMillis());
                
                return ServerSentEvent.<Map<String, Object>>builder()
                    .data(data)
                    .id(requestId)
                    .event("status_update")
                    .build();
            })
            .doOnComplete(() -> logger.info("SSE stream completed for request ID: {}", requestId))
            .doOnError(error -> logger.error("SSE stream error for request ID: {}", requestId, error));
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


}
