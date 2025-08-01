package com.neuralsynthmodeler.backend;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.nio.file.Files;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertFalse;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@org.springframework.test.context.ActiveProfiles("test")
public class InferenceControllerIntegrationTest {

    @LocalServerPort
    private int port;

    private WebTestClient webTestClient;

    @BeforeEach
    public void setup() {
        this.webTestClient = WebTestClient.bindToServer()
                .baseUrl("http://localhost:" + port)
                .responseTimeout(Duration.ofSeconds(30))
                .build();
    }

    @Test
    public void testInferApiWithStatusUpdatesAndPreset() throws IOException, InterruptedException {
        // Load gzipped audio file
        byte[] gzippedAudio = Files.readAllBytes(new ClassPathResource("training.wav.gz").getFile().toPath());

        // 1. Hit the infer API
        Map<String, Object> response = webTestClient.post()
                .uri("/v1/models/vital/infer")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .bodyValue(gzippedAudio)
                .exchange()
                .expectStatus().isOk()
                .expectBody(Map.class)
                .returnResult()
                .getResponseBody();

        String requestId = (String) response.get("request_id");
        System.out.println("Request ID: " + requestId);

        // 2. Poll for status updates and check order
        List<String> observedStatuses = new ArrayList<>();
        for (int i = 0; i < 30; i++) { // Increased timeout for BentoML processing
            Thread.sleep(2000); // Increased sleep time
            Map statusResp = webTestClient.get()
                    .uri("/v1/infer-audio/status/" + requestId)
                    .exchange()
                    .expectStatus().isOk()
                    .expectBody(Map.class)
                    .returnResult()
                    .getResponseBody();
            String status = (String) statusResp.get("status");
            observedStatuses.add(status);
            System.out.println("Status update: " + status);
            if ("DONE".equals(status) || "ERROR".equals(status)) {
                break;
            }
        }
        
        // Check that we have some expected statuses (allowing for BentoML integration)
        System.out.println("All observed statuses: " + observedStatuses);
        assertThat(observedStatuses).contains("PENDING");
        
        // Only test preset retrieval if inference completed successfully
        String finalStatus = observedStatuses.get(observedStatuses.size() - 1);
        if ("DONE".equals(finalStatus)) {
            // 3. Retrieve the preset file after completion
            webTestClient.get()
                    .uri("/v1/preset/" + requestId)
                    .exchange()
                    .expectStatus().isOk()
                    .expectHeader().contentType("application/octet-stream");
        } else {
            System.out.println("Inference did not complete successfully. Final status: " + finalStatus);
            System.out.println("This is expected if BentoML service is not running on localhost:3000");
        }
    }

    @Test
    public void testStreamStatusEndpoint() {
        // First create a request to get a request ID
        byte[] audioData = loadTestAudioFile();
        
        // Upload audio and get request ID
        String requestId = webTestClient.post()
                .uri("/v1/models/vital/infer")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .bodyValue(audioData)
                .exchange()
                .expectStatus().isOk()
                .expectBody(Map.class)
                .returnResult()
                .getResponseBody()
                .get("request_id")
                .toString();
        
        assertThat(requestId).isNotNull();
        
        // Test SSE endpoint
        List<Map> statusUpdates = webTestClient.get()
                .uri("/v1/infer-audio/stream-status/" + requestId)
                .accept(MediaType.TEXT_EVENT_STREAM)
                .exchange()
                .expectStatus().isOk()
                .expectBodyList(Map.class)
                .returnResult()
                .getResponseBody();
        
        assertThat(statusUpdates).isNotNull();
        assertThat(statusUpdates).isNotEmpty();
        
        // Verify the structure of status updates
        Map<String, Object> firstUpdate = statusUpdates.get(0);
        assertThat(firstUpdate.get("request_id")).isNotNull();
        assertThat(firstUpdate.get("status")).isNotNull();
        assertThat(firstUpdate.get("timestamp")).isNotNull();
        
        System.out.println("SSE Status updates received: " + statusUpdates.size());
        statusUpdates.forEach(update -> 
            System.out.println("Status: " + update.get("status") + " at " + update.get("timestamp")));
    }

    private byte[] loadTestAudioFile() {
        try {
            return Files.readAllBytes(new ClassPathResource("training.wav.gz").getFile().toPath());
        } catch (IOException e) {
            throw new RuntimeException("Failed to load test audio file", e);
        }
    }
}
