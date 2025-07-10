package com.neuralsynthmodeler.backend.service;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;
import java.time.Duration;

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
        logger.info("getStatusStream called for request ID: {}", requestId);
        Sinks.Many<InferenceService.RequestStatus> sink = statusSinks.computeIfAbsent(requestId, 
            id -> {
                logger.info("Creating new sink for request ID: {}", id);
                return Sinks.many().multicast().onBackpressureBuffer();
            });
        
        // Emit current status immediately if available
        InferenceService.RequestStatus currentStatus = requestStatusMap.get(requestId);
        if (currentStatus != null) {
            logger.info("Emitting current status for request ID {}: {}", requestId, currentStatus);
            sink.tryEmitNext(currentStatus);
        } else {
            logger.info("No current status found for request ID: {}", requestId);
        }
        
        return sink.asFlux()
            .doFinally(signalType -> {
                // Clean up sink when stream ends
                statusSinks.remove(requestId);
                logger.info("Cleaned up status sink for request ID: {} (signal: {})", requestId, signalType);
            });
    }

    /**
     * Update status and emit to subscribers - Single source of truth
     */
    public void updateStatus(String requestId, InferenceService.RequestStatus status) {
        logger.info("updateStatus called for request ID {} with status: {}", requestId, status);
        // Update the single source of truth
        requestStatusMap.put(requestId, status);
        // Emit to reactive subscribers
        Sinks.Many<InferenceService.RequestStatus> sink = statusSinks.get(requestId);
        if (sink != null) {
            sink.tryEmitNext(status);
            logger.info("Emitted status update for request ID {}: {}", requestId, status);
            // If status is final, delay before completing the sink so the last event is flushed
            if (status == InferenceService.RequestStatus.DONE || status == InferenceService.RequestStatus.ERROR) {
                logger.info("Final status detected, scheduling sink completion for request ID: {}", requestId);
                // Delay completion by 500ms to ensure frontend receives the final status
                new Thread(() -> {
                    try { Thread.sleep(500); } catch (InterruptedException ignored) {}
                    sink.tryEmitComplete();
                    statusSinks.remove(requestId);
                    logger.info("Completed and removed status sink for request ID: {}", requestId);
                }).start();
            }
        } else {
            logger.warn("No sink found for request ID: {} when updating status to: {}", requestId, status);
            // If no sink, just remove if final
            if (status == InferenceService.RequestStatus.DONE || status == InferenceService.RequestStatus.ERROR) {
                statusSinks.remove(requestId);
                logger.debug("Removed status sink for completed request ID: {} (no sink found)", requestId);
            }
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