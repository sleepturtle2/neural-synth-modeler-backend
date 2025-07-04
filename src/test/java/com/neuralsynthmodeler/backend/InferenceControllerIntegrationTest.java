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

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
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
        for (int i = 0; i < 10; i++) {
            Thread.sleep(1000);
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
            if ("DONE".equals(status)) {
                break;
            }
        }
        // Check that statuses are in expected order
        List<String> expectedOrder = List.of("RECEIVED", "PROCESSING", "DONE");
        assertThat(observedStatuses).containsSequence(expectedOrder);

        // 3. Retrieve the preset file after completion
        webTestClient.get()
                .uri("/v1/preset/" + requestId)
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentType("application/octet-stream");
    }
} 