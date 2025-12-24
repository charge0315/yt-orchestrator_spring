package com.charge0315.yt.controller;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.WebSession;

import com.charge0315.yt.util.SessionAuth;
import com.charge0315.yt.service.YouTubeDataApiService;

import reactor.core.publisher.Mono;

/**
 * YouTube Music 互換API（最小実装）。
 *
 * <p>Node版ではYouTube Music用のキャッシュ等が存在しますが、Spring版では
 * まず YouTube Data API を直接参照して互換のレスポンス形を返します。</p>
 */
@RestController
@RequestMapping("/api/ytmusic")
public class YtMusicController {

    private final YouTubeDataApiService youTubeDataApiService;

    public YtMusicController(YouTubeDataApiService youTubeDataApiService) {
        this.youTubeDataApiService = youTubeDataApiService;
    }

    /**
     * YouTube Music連携状態（最小互換）を返します。
     */
    @GetMapping("/auth/status")
    Mono<Map<String, Object>> authStatus(WebSession session) {
        SessionAuth.requireUserId(session);
        return Mono.just(Map.of(
            "connected", true,
                "message", "YouTube Data API v3 を利用中のため、常に接続済みです"));
    }

    /**
     * プレイリスト一覧を返します（最小互換・Data API 直参照）。
     */
    @GetMapping("/playlists")
    Mono<Map<String, Object>> playlists(
            @RequestParam(name = "refresh", required = false) String refresh,
            @RequestParam(name = "force", required = false) String force,
            WebSession session) {
        SessionAuth.requireUserId(session);
        String accessToken = SessionAuth.requireYouTubeAccessToken(session);
        // Node 版は Mongo キャッシュ優先だが、Spring 版はYouTube Data API直参照（最小互換）
        return youTubeDataApiService
            .listPlaylists(accessToken, null)
            .map(r -> Map.of(
                "items", r.items(),
                "nextPageToken", r.nextPageToken()));
    }

    /**
     * 指定プレイリストの詳細（name/songs 等）を返します（最小互換）。
     */
    @GetMapping("/playlists/{id}")
    Mono<ResponseEntity<Map<String, Object>>> playlistDetail(@PathVariable("id") String id, WebSession session) {
        SessionAuth.requireUserId(session);
        if (id == null || id.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "id_required");
        }

        String accessToken = SessionAuth.requireYouTubeAccessToken(session);

        Mono<com.fasterxml.jackson.databind.JsonNode> playlistMono = youTubeDataApiService.getPlaylist(accessToken, id);
        Mono<List<YouTubeDataApiService.PlaylistVideo>> itemsMono = youTubeDataApiService.listPlaylistItems(accessToken,
            id);

        return Mono.zip(playlistMono, itemsMono)
            .map(tuple -> {
                com.fasterxml.jackson.databind.JsonNode playlist = tuple.getT1();
                List<YouTubeDataApiService.PlaylistVideo> items = tuple.getT2();

                String title = playlist.path("snippet").path("title").asText("");
                String description = playlist.path("snippet").path("description").asText("");
                String thumb = playlist.path("snippet").path("thumbnails").path("default").path("url").asText(null);
                if (thumb == null) {
                thumb = playlist.path("snippet").path("thumbnails").path("medium").path("url").asText(null);
                }

                List<Map<String, Object>> songs = items.stream()
                    .map(v -> Map.<String, Object>of(
                        "videoId", v.videoId(),
                        "title", v.title(),
                        "artist", v.channelTitle() != null ? v.channelTitle() : "不明なアーティスト",
                        "thumbnail", v.thumbnail(),
                        "duration", v.duration(),
                        "addedAt", v.publishedAt()))
                    .collect(Collectors.toList());

                Map<String, Object> out = Map.of(
                    "_id", playlist.path("id").asText(id),
                    "name", title,
                    "description", description,
                    "thumbnail", thumb,
                    "songs", songs,
                    "createdAt", playlist.path("snippet").path("publishedAt").asText(null),
                    "updatedAt", java.time.Instant.now().toString());

                return ResponseEntity.ok(out);
            });
    }

    /**
     * クエリで動画を検索し、フロント互換の配列形式で返します。
     */
    @GetMapping("/search")
    Mono<List<Map<String, Object>>> search(@RequestParam(name = "query", required = false) String query, WebSession session) {
        SessionAuth.requireUserId(session);
        if (query == null || query.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "検索クエリが必要です");
        }
        String accessToken = SessionAuth.requireYouTubeAccessToken(session);
        return youTubeDataApiService
            .searchVideos(accessToken, query, 20)
            .map(results -> results.stream()
                .map(v -> Map.<String, Object>of(
                    "videoId", v.videoId(),
                    "title", v.title(),
                    "artist", v.channelTitle() != null ? v.channelTitle() : "不明なアーティスト",
                    "thumbnail", v.thumbnail()))
                        .collect(Collectors.toList()));
    }
}
