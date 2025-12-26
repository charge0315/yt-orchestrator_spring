package com.charge0315.yt.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.reactive.server.WebTestClient;

/**
 * HealthController のテスト。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
class HealthControllerTest {

    @Autowired
    private WebTestClient webTestClient;

    @Test
    @DisplayName("ヘルスチェックエンドポイントが正常なレスポンスを返すこと")
    void health_shouldReturnOkStatus() {
        webTestClient
                .get()
                .uri("/api/health")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.status").isEqualTo("ok")
                .jsonPath("$.message").isNotEmpty();
    }

    @Test
    @DisplayName("ヘルスチェックエンドポイントのレスポンスに必須フィールドが含まれること")
    void health_shouldContainRequiredFields() {
        webTestClient
                .get()
                .uri("/api/health")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.status").exists()
                .jsonPath("$.message").exists();
    }
}
