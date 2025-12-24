package com.charge0315.yt.controller;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.WebSession;
import org.springframework.web.server.ResponseStatusException;

import com.charge0315.yt.service.ChannelCacheService;
import com.charge0315.yt.util.SessionAuth;

import reactor.core.publisher.Mono;

/**
 * チャンネル登録API。
 *
 * <p>フロントのチャンネル管理画面から利用され、登録/解除と登録済み一覧を提供します。
 * 登録データは MongoDB にキャッシュします。</p>
 */
@RestController
@RequestMapping("/api/channels")
public class ChannelsController {

    private final ChannelCacheService channelCacheService;

    public ChannelsController(ChannelCacheService channelCacheService) {
        this.channelCacheService = channelCacheService;
    }

    /**
     * 登録済みチャンネル一覧を返します。
     */
    @GetMapping
    Mono<List<Map<String, Object>>> list(WebSession session) {
        String userId = SessionAuth.requireUserId(session);
        return channelCacheService
                .listChannels(userId)
                .map(items -> items.stream().map(ChannelsController::toChannelResponse).toList());
    }

    /**
     * チャンネルを登録（購読）します。
     */
    @PostMapping
    Mono<Map<String, Object>> subscribe(@RequestBody SubscribeRequest body, WebSession session) {
        String userId = SessionAuth.requireUserId(session);
        String accessToken = SessionAuth.requireYouTubeAccessToken(session);
        if (body == null || body.channelId() == null || body.channelId().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "channelId_required");
        }
        return channelCacheService
                .subscribe(userId, accessToken, body.channelId())
                .map(ChannelsController::toChannelResponse);
    }

    /**
     * 登録済みチャンネルを解除します。
     */
    @DeleteMapping("/{id}")
    Mono<Map<String, Object>> unsubscribe(@PathVariable("id") String id, WebSession session) {
        String userId = SessionAuth.requireUserId(session);
        if (id == null || id.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "id_required");
        }
        return channelCacheService
                .unsubscribe(userId, id)
                .thenReturn(Map.of("ok", true));
    }

    private static Map<String, Object> toChannelResponse(ChannelCacheService.CachedChannelEntry ch) {
        Map<String, Object> snippet = new HashMap<>();
        snippet.put("resourceId", Map.of("channelId", ch.channelId()));
        snippet.put("title", ch.channelTitle());
        snippet.put("description", ch.channelDescription());
        snippet.put("thumbnails", Map.of(
                "default", Map.of("url", ch.thumbnailUrl()),
                "medium", Map.of("url", ch.thumbnailUrl()),
                "high", Map.of("url", ch.thumbnailUrl())));

        Map<String, Object> out = new HashMap<>();
        out.put("id", ch.subscriptionId());
        out.put("latestVideoId", ch.latestVideoId());
        out.put("latestVideoThumbnail", ch.latestVideoThumbnail());
        out.put("latestVideoTitle", ch.latestVideoTitle());
        out.put("snippet", snippet);
        out.put("contentDetails", Map.of("totalItemCount", ch.videoCount()));
        return out;
    }

    record SubscribeRequest(String channelId) {
    }
}
