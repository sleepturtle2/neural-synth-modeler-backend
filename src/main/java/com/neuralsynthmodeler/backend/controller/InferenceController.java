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

@RestController
@RequestMapping("/v1")
public class InferenceController {

    private final InferenceService inferenceService;
    private static final String SUPPORTED_MODEL = "vital";
    private static final Logger logger = LoggerFactory.getLogger(InferenceController.class);

    @Autowired
    public InferenceController(InferenceService inferenceService) {
        this.inferenceService = inferenceService;
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
} 