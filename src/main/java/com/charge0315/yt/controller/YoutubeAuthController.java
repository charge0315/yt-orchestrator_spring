package com.charge0315.yt.controller;

import java.time.Instant;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebSession;

import com.charge0315.yt.service.GoogleOAuthService;

import reactor.core.publisher.Mono;

/**
 * YouTube OAuth（フロント互換）に関するエンドポイント。
 *
 * <p>フロント互換のため、以下のAPIを提供します。</p>
 * <ul>
 *   <li>{@code GET /api/youtube/auth/url} - 認可URLの取得</li>
 *   <li>{@code POST /api/youtube/auth/callback} - code をトークンへ交換し、セッションへ保存</li>
 *   <li>{@code GET /api/youtube/auth/status} - 接続状態（有効期限を含む）</li>
 * </ul>
 *
 * <p>内部実装は {@link GoogleOAuthService} に寄せており、
 * 取得したアクセストークン/リフレッシュトークン等を {@link WebSession} に保存します。</p>
 */
@RestController
@RequestMapping("/api/youtube/auth")
public class YoutubeAuthController {

    private final GoogleOAuthService googleOAuthService;

    public YoutubeAuthController(GoogleOAuthService googleOAuthService) {
        this.googleOAuthService = googleOAuthService;
    }

    /**
     * YouTube連携用の認可URLを返します。
     */
    @GetMapping("/url")
    Mono<Map<String, Object>> getAuthUrl(ServerWebExchange exchange) {
        return Mono.fromSupplier(() -> googleOAuthService.buildAuthorizationUri(exchange))
            .map(uri -> Map.<String, Object>of("url", uri.toString()));
    }

    public record CallbackRequest(String code) {
    }

    /**
     * 認可コードをトークンへ交換し、セッションへ保存します。
     */
    @PostMapping("/callback")
    Mono<Map<String, Object>> callback(
            @RequestBody Mono<CallbackRequest> body,
            ServerWebExchange exchange,
            WebSession session) {
        return body.flatMap(req -> {
            String code = req != null ? req.code() : null;
            if (code == null || code.isBlank()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "code_required");
            }

            return googleOAuthService
                .exchangeCodeForTokens(exchange, code)
                .flatMap(tokens -> googleOAuthService
                    .fetchUserInfo(tokens.accessToken())
                    .map(userInfo -> {
                        session.getAttributes().put("userId", userInfo.id());
                        session.getAttributes().put("email", userInfo.email());
                        session.getAttributes().put("name", userInfo.name());
                        session.getAttributes().put("picture", userInfo.picture());
                        session.getAttributes().put("youtubeAccessToken", tokens.accessToken());
                        session.getAttributes().put("youtubeRefreshToken", tokens.refreshToken());
                        session.getAttributes().put("youtubeTokenExpiry", tokens.expiresAt());
                        return Map.<String, Object>of("ok", true);
                    }));
        });
    }

    /**
     * YouTube連携状態（connected）を返します。
     */
    @GetMapping("/status")
    Mono<Map<String, Object>> status(WebSession session) {
        String accessToken = session.getAttribute("youtubeAccessToken");
        Instant expiry = session.getAttribute("youtubeTokenExpiry");

        boolean connected = accessToken != null && !accessToken.isBlank() && (expiry == null || expiry.isAfter(Instant.now()));

        if (expiry != null) {
            return Mono.just(Map.of(
                "connected", connected,
                "expiresAt", expiry.toString()));
        }
        return Mono.just(Map.of("connected", connected));
    }
}
