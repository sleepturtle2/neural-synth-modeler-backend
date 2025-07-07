package com.neuralsynthmodeler.backend;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.junit.jupiter.api.BeforeEach;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
public class SimpleControllerTest {

    @LocalServerPort
    private int port;

    private WebTestClient webTestClient;

    @BeforeEach
    public void setup() {
        this.webTestClient = WebTestClient.bindToServer()
                .baseUrl("http://localhost:" + port)
                .build();
    }

    @Test
    public void testServerMetadata() {
        webTestClient.get()
                .uri("/v1")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.name").isEqualTo("neural-synth-java-backend")
                .jsonPath("$.version").isEqualTo("0.0.1");
    }

    @Test
    public void testHealthLive() {
        webTestClient.get()
                .uri("/v1/health/live")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.live").isEqualTo(true);
    }

    @Test
    public void testModelMetadata() {
        webTestClient.get()
                .uri("/v1/models/vital")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.name").isEqualTo("vital");
    }
}
