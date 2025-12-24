package com.charge0315.yt.controller;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.WebSession;

import com.charge0315.yt.util.SessionAuth;
import com.charge0315.yt.service.CacheRefreshService;

import reactor.core.publisher.Mono;

/**
 * キャッシュ操作API。
 *
 * <p>MongoDB に保存しているチャンネルキャッシュを、YouTube Data API を用いて更新します。</p>
 */
@RestController
@RequestMapping("/api/cache")
public class CacheController {

    private final CacheRefreshService cacheRefreshService;

    public CacheController(CacheRefreshService cacheRefreshService) {
        this.cacheRefreshService = cacheRefreshService;
    }

    /**
     * 登録済みチャンネルのキャッシュを更新します。
     */
    @PostMapping("/refresh")
    Mono<ResponseEntity<Map<String, Object>>> refresh(WebSession session) {
        String userId = SessionAuth.requireUserId(session);
        String accessToken = SessionAuth.getYouTubeAccessTokenOrNull(session);

        return cacheRefreshService
            .refreshUserCache(userId, accessToken)
            .map(r -> {
                if (!r.ok() && "youtube_access_token_missing".equals(r.error())) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("ok", false, "error", r.error()));
                }
                if (!r.ok() && "mongodb_not_connected".equals(r.error())) {
                return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(Map.of("ok", false, "error", r.error()));
                }
                if (!r.ok()) {
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("ok", false, "error", r.error()));
                }
                return ResponseEntity.ok(Map.of(
                    "ok", true,
                    "stats", Map.of(
                        "checked", r.checked(),
                        "updated", r.updated())));
            });
    }
}
