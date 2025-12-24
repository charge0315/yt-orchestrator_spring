package com.charge0315.yt.controller;

import java.net.URI;
import java.time.Instant;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebSession;
import org.springframework.web.util.UriComponentsBuilder;

import com.charge0315.yt.service.GoogleOAuthService;

import reactor.core.publisher.Mono;

/**
 * 認証（Google OAuth）に関するエンドポイント。
 *
 * <p>主に以下を提供します。</p>
 * <ul>
 *   <li>{@code GET /api/auth/google} - Google OAuth 開始（リダイレクト）</li>
 *   <li>{@code GET /api/auth/google/callback} - OAuth コールバック</li>
 *   <li>{@code GET /api/auth/me} - 現在ログイン中のユーザー情報</li>
 *   <li>{@code POST /api/auth/logout} - ログアウト</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final GoogleOAuthService googleOAuthService;

    public AuthController(GoogleOAuthService googleOAuthService) {
        this.googleOAuthService = googleOAuthService;
    }

    @PostMapping("/logout")
    Mono<Map<String, Object>> logout(WebSession session) {
        return session.invalidate()
                .thenReturn(Map.of("message", "ログアウトしました"));
    }

    @GetMapping("/me")
    Mono<Map<String, Object>> me(WebSession session) {
        String userId = session.getAttribute("userId");
        if (userId == null || userId.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }

        String email = session.getAttribute("email");
        String name = session.getAttribute("name");
        String picture = session.getAttribute("picture");

        boolean reauthRequired = false;
        String reauthReason = null;
        String reauthMessage = null;

        String accessToken = session.getAttribute("youtubeAccessToken");
        Instant expiry = session.getAttribute("youtubeTokenExpiry");
        Instant now = Instant.now();
        if (accessToken == null || accessToken.isBlank()) {
            reauthRequired = true;
            reauthReason = "missing";
            reauthMessage = "YouTube連携が未設定です。Googleでログインしてください。";
        } else if (expiry != null && !expiry.isAfter(now)) {
            reauthRequired = true;
            reauthReason = "expired";
            reauthMessage = "アクセストークンの有効期限が切れています。再ログインしてください。";
        }

        Map<String, Object> user = Map.of(
                "id", userId,
                "email", email,
                "name", name,
                "picture", picture,
                "reauthRequired", reauthRequired,
                "reauthReason", reauthReason,
                "reauthMessage", reauthMessage);

        return Mono.just(Map.of("user", user));
    }

    @GetMapping("/google")
    Mono<ResponseEntity<Void>> googleOAuthStart(ServerWebExchange exchange) {
        try {
            URI uri = googleOAuthService.buildAuthorizationUri(exchange);
            return Mono.just(ResponseEntity.status(HttpStatus.FOUND)
                    .location(uri)
                    .<Void>build());
        } catch (IllegalStateException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, e.getMessage(), e);
        }
    }

    @GetMapping("/google/callback")
    Mono<ResponseEntity<Void>> googleOAuthCallback(
            @RequestParam(name = "code", required = false) String code,
            @RequestParam(name = "error", required = false) String error,
            ServerWebExchange exchange,
            WebSession session) {
        String frontendUrl = System.getenv("FRONTEND_URL");
        String base = (frontendUrl != null && !frontendUrl.isBlank()) ? frontendUrl : "";
        if (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        // フロントは /auth/callback ルートを持つため、そこへ戻す（エラー表示も可能）
        URI successRedirect = URI.create((base.isBlank() ? "" : base) + "/auth/callback");

        if (error != null && !error.isBlank()) {
            return Mono.just(ResponseEntity.status(HttpStatus.FOUND)
                    .location(appendQuery(successRedirect, "error", error))
                    .<Void>build());
        }

        if (code == null || code.isBlank()) {
            return Mono.just(ResponseEntity.status(HttpStatus.FOUND)
                    .location(appendQuery(successRedirect, "error", "no_code"))
                    .<Void>build());
        }

        return googleOAuthService
                .exchangeCodeForTokens(exchange, code)
                .flatMap(tokens -> googleOAuthService.fetchUserInfo(tokens.accessToken())
                        .flatMap(userInfo -> {
                            session.getAttributes().put("userId", userInfo.id());
                            session.getAttributes().put("email", userInfo.email());
                            session.getAttributes().put("name", userInfo.name());
                            session.getAttributes().put("picture", userInfo.picture());
                            session.getAttributes().put("youtubeAccessToken", tokens.accessToken());
                            session.getAttributes().put("youtubeRefreshToken", tokens.refreshToken());
                            session.getAttributes().put("youtubeTokenExpiry", tokens.expiresAt());
                            return Mono.just(ResponseEntity.status(HttpStatus.FOUND)
                                    .location(successRedirect)
                                    .<Void>build());
                        }))
                        .onErrorResume(e -> Mono.just(ResponseEntity.status(HttpStatus.FOUND)
                            .location(appendQuery(successRedirect, "error", "auth_failed"))
                            .<Void>build()));
    }

    private static URI appendQuery(URI base, String key, String value) {
        return UriComponentsBuilder
                .fromUri(base)
                .queryParam(key, value)
                .build(true)
                .toUri();
    }
}
