package com.charge0315.yt.controller;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.reactive.server.WebTestClient;

/**
 * AuthController のテスト。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
class AuthControllerTest {

    @Autowired
    private WebTestClient webTestClient;

    @Test
    @DisplayName("未ログイン状態で /api/auth/me は401エラーを返すこと")
    void authMe_withoutLogin_shouldReturn401() {
        webTestClient
                .get()
                .uri("/api/auth/me")
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    @Disabled("Google OAuth設定が必要なためスキップ")
    @DisplayName("/api/auth/google エンドポイントが存在すること")
    void googleAuth_endpointExists() {
        // Google OAuth設定が完了していない環境では500エラーが返される
        webTestClient
                .get()
                .uri("/api/auth/google")
                .exchange()
                .expectStatus().is3xxRedirection();
    }

    @Test
    @DisplayName("ログアウトエンドポイントが存在すること")
    void logout_endpointExists() {
        webTestClient
                .post()
                .uri("/api/auth/logout")
                .exchange()
                .expectStatus().isOk();
    }
}
