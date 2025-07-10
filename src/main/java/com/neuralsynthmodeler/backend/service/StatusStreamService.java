package com.neuralsynthmodeler.backend.service;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

@Service
public class StatusStreamService {

    private static final Logger logger = LoggerFactory.getLogger(StatusStreamService.class);
    
    // Single source of truth for status management
    private final Map<String, InferenceService.RequestStatus> requestStatusMap = new ConcurrentHashMap<>();
    // Store reactive sinks for real-time status updates
    private final Map<String, Sinks.Many<InferenceService.RequestStatus>> statusSinks = new ConcurrentHashMap<>();

    /**
     * Get a Flux for real-time status updates for a specific request
     */
    public Flux<InferenceService.RequestStatus> getStatusStream(String requestId) {
        Sinks.Many<InferenceService.RequestStatus> sink = statusSinks.computeIfAbsent(requestId, 
            id -> Sinks.many().multicast().onBackpressureBuffer());
        
        // Emit current status immediately if available
        InferenceService.RequestStatus currentStatus = requestStatusMap.get(requestId);
        if (currentStatus != null) {
            sink.tryEmitNext(currentStatus);
        }
        
        return sink.asFlux()
            .doFinally(signalType -> {
                // Clean up sink when stream ends
                statusSinks.remove(requestId);
                logger.debug("Cleaned up status sink for request ID: {}", requestId);
            });
    }

    /**
     * Update status and emit to subscribers - Single source of truth
     */
    public void updateStatus(String requestId, InferenceService.RequestStatus status) {
        // Update the single source of truth
        requestStatusMap.put(requestId, status);
        
        // Emit to reactive subscribers
        Sinks.Many<InferenceService.RequestStatus> sink = statusSinks.get(requestId);
        if (sink != null) {
            sink.tryEmitNext(status);
            logger.debug("Emitted status update for request ID {}: {}", requestId, status);
        }
        
        // Clean up sink if status is final
        if (status == InferenceService.RequestStatus.DONE || status == InferenceService.RequestStatus.ERROR) {
            statusSinks.remove(requestId);
            logger.debug("Removed status sink for completed request ID: {}", requestId);
        }
    }

    /**
     * Get current status for a request ID
     */
    public InferenceService.RequestStatus getStatus(String requestId) {
        return requestStatusMap.get(requestId);
    }

    /**
     * Clear status for a request ID (for cleanup)
     */
    public void clearStatus(String requestId) {
        requestStatusMap.remove(requestId);
        statusSinks.remove(requestId);
        logger.debug("Cleared status for request ID: {}", requestId);
    }
} 