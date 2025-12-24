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

    @GetMapping("/")
    Mono<String> home() {
        return Mono.just("yt-orchestrator (spring webflux) is running");
    }

    @GetMapping("/api/health")
    Mono<Map<String, Object>> health() {
        return Mono.just(Map.of(
                "status", "ok",
                "message", "YouTube Orchestrator API は稼働中です"));
    }
}
