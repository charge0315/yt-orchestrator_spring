package com.charge0315.yt.controller;

import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import reactor.core.publisher.Mono;

/**
 * ヘルスチェック用エンドポイント。
 *
 * <p>ロードバランサや疎通確認用途で利用します。</p>
 */
@RestController
public class HealthController {
    /**
     * ヘルスチェック（稼働確認）結果を返します。
     */
    @GetMapping("/api/health")
    Mono<Map<String, Object>> health() {
        return Mono.just(Map.of(
                "status", "ok",
                "message", "YouTube Orchestrator API は稼働中です"));
    }
}
