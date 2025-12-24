package com.charge0315.yt.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.WebSession;

import com.charge0315.yt.service.YouTubeChannelsService;
import com.charge0315.yt.util.SessionAuth;

import reactor.core.publisher.Mono;

/**
 * YouTubeチャンネル管理API（フロント互換）。
 *
 * <p>フロントの {@code youtubeChannelsApi} が利用する
 * {@code /api/youtube/channels} 一式を提供します。</p>
 */
@RestController
@RequestMapping("/api/youtube/channels")
public class YoutubeChannelsController {

    private final YouTubeChannelsService youTubeChannelsService;

    public YoutubeChannelsController(YouTubeChannelsService youTubeChannelsService) {
        this.youTubeChannelsService = youTubeChannelsService;
    }

    /**
     * 登録済みYouTubeチャンネル一覧を返します。
     */
    @GetMapping
    Mono<List<YouTubeChannelsService.YouTubeChannelEntry>> getAll(WebSession session) {
        String userId = SessionAuth.requireUserId(session);
        String accessToken = SessionAuth.getYouTubeAccessTokenOrNull(session);
        return youTubeChannelsService.getAll(userId, accessToken);
    }

    /**
     * YouTubeチャンネルを登録（購読）します。
     */
    @PostMapping
    Mono<YouTubeChannelsService.YouTubeChannelEntry> subscribe(
            @RequestBody Mono<YouTubeChannelsService.SubscribeRequest> body,
            WebSession session) {
        String userId = SessionAuth.requireUserId(session);
        String accessToken = SessionAuth.requireYouTubeAccessToken(session);
        return body.flatMap(req -> youTubeChannelsService.subscribe(userId, accessToken, req));
    }

    /**
     * 登録済みYouTubeチャンネルを解除します。
     */
    @DeleteMapping("/{id}")
    Mono<Void> unsubscribe(@PathVariable("id") String id, WebSession session) {
        String userId = SessionAuth.requireUserId(session);
        if (id == null || id.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "id_required");
        }
        return youTubeChannelsService.unsubscribe(userId, id);
    }

    /**
     * 登録チャンネルの最新動画一覧を返します。
     */
    @GetMapping("/latest-videos")
    Mono<List<YouTubeChannelsService.LatestVideo>> getLatestVideos(WebSession session) {
        String userId = SessionAuth.requireUserId(session);
        String accessToken = SessionAuth.getYouTubeAccessTokenOrNull(session);
        return youTubeChannelsService.getLatestVideos(userId, accessToken);
    }

    public record UpdateVideosRequest(List<YouTubeChannelsService.LatestVideo> latestVideos) {
    }

    /**
     * 最新動画情報（表示用キャッシュ）を更新します。
     */
    @PostMapping("/{id}/update-videos")
    Mono<YouTubeChannelsService.YouTubeChannelEntry> updateVideos(
            @PathVariable("id") String id,
            @RequestBody Mono<UpdateVideosRequest> body,
            WebSession session) {
        String userId = SessionAuth.requireUserId(session);
        if (id == null || id.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "id_required");
        }
        return body.flatMap(req -> youTubeChannelsService.updateVideos(userId, id, req != null ? req.latestVideos() : null));
    }
}
