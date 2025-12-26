package com.charge0315.yt.controller;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.reactive.server.WebTestClient;

/**
 * YoutubeAuthController のテスト。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
class YoutubeAuthControllerTest {

    @Autowired
    private WebTestClient webTestClient;

    @Test
    @Disabled("OAuth設定が必要なためスキップ")
    @DisplayName("YouTube認証URLエンドポイントが正常に動作すること")
    void youtubeAuthUrl_shouldReturnAuthUrl() {
        // OAuth設定が完了していない環境では500エラーが返される可能性がある
        webTestClient
                .get()
                .uri("/api/youtube/auth/url")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.url").exists()
                .jsonPath("$.url").isNotEmpty();
    }

    @Test
    @DisplayName("YouTube認証ステータスエンドポイントが存在すること")
    void youtubeAuthStatus_shouldReturnStatus() {
        webTestClient
                .get()
                .uri("/api/youtube/auth/status")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.connected").exists();
    }
}
