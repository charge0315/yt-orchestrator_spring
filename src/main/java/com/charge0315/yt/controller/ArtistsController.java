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
 * アーティスト（= チャンネル）管理API。
 *
 * <p>MongoDB のキャッシュ（登録済みチャンネル情報）を元に、
 * アーティストとしての登録/解除や最新動画（新着）を提供します。</p>
 */
@RestController
@RequestMapping("/api/artists")
public class ArtistsController {

    private final ChannelCacheService channelCacheService;

    public ArtistsController(ChannelCacheService channelCacheService) {
        this.channelCacheService = channelCacheService;
    }

    /**
     * 登録済みアーティスト一覧を返します。
     */
    @GetMapping
    Mono<List<Map<String, Object>>> list(WebSession session) {
        String userId = SessionAuth.requireUserId(session);
        return channelCacheService
                .listArtists(userId)
                .map(items -> items.stream().map(ArtistsController::toArtistResponse).toList());
    }

    /**
     * 指定チャンネルをアーティストとして登録します。
     */
    @PostMapping
    Mono<Map<String, Object>> markAsArtist(@RequestBody MarkArtistRequest body, WebSession session) {
        String userId = SessionAuth.requireUserId(session);
        if (body == null || body.channelId() == null || body.channelId().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "channelId_required");
        }
        return channelCacheService
                .setArtistFlag(userId, body.channelId(), true)
                .thenReturn(Map.of("ok", true));
    }

    /**
     * アーティスト登録を解除します。
     */
    @DeleteMapping("/{id}")
    Mono<Map<String, Object>> unmarkAsArtist(@PathVariable("id") String id, WebSession session) {
        String userId = SessionAuth.requireUserId(session);
        if (id == null || id.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "id_required");
        }
        return channelCacheService
                .setArtistFlagById(userId, id, false)
                .thenReturn(Map.of("ok", true));
    }

    /**
     * 登録アーティストの新着（最新動画）一覧を返します。
     */
    @GetMapping("/new-releases")
    Mono<List<Map<String, Object>>> newReleases(WebSession session) {
        String userId = SessionAuth.requireUserId(session);
        String accessToken = SessionAuth.getYouTubeAccessTokenOrNull(session);
        return channelCacheService
            .listNewReleases(userId, accessToken)
                .map(items -> items.stream().map(ArtistsController::toNewReleaseResponse).toList());
    }

    private static Map<String, Object> toArtistResponse(ChannelCacheService.CachedChannelEntry ch) {
        Map<String, Object> snippet = new HashMap<>();
        snippet.put("resourceId", Map.of("channelId", ch.channelId()));
        snippet.put("title", ch.channelTitle());
        snippet.put("thumbnails", Map.of("default", Map.of("url", ch.thumbnailUrl())));

        Map<String, Object> out = new HashMap<>();
        out.put("id", ch.subscriptionId());
        out.put("latestVideoId", ch.latestVideoId());
        out.put("latestVideoThumbnail", ch.latestVideoThumbnail());
        out.put("latestVideoTitle", ch.latestVideoTitle());
        out.put("snippet", snippet);
        return out;
    }

    private static Map<String, Object> toNewReleaseResponse(ChannelCacheService.CachedChannelEntry ch) {
        Map<String, Object> snippet = new HashMap<>();
        snippet.put("title", ch.latestVideoTitle());
        snippet.put("thumbnails", Map.of("medium", Map.of("url", ch.latestVideoThumbnail())));
        snippet.put("channelTitle", ch.channelTitle());
        snippet.put("channelId", ch.channelId());
        snippet.put("publishedAt", ch.latestVideoPublishedAt());

        Map<String, Object> out = new HashMap<>();
        out.put("id", Map.of("videoId", ch.latestVideoId()));
        out.put("videoId", ch.latestVideoId());
        out.put("duration", ch.latestVideoDuration());
        out.put("viewCount", ch.latestVideoViewCount());
        out.put("snippet", snippet);
        return out;
    }

    record MarkArtistRequest(String channelId) {
    }
}
