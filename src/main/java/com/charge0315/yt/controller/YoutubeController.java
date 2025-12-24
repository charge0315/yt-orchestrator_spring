package com.charge0315.yt.controller;

import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.WebSession;

import com.charge0315.yt.util.SessionAuth;
import com.charge0315.yt.service.YouTubeDataApiService;

import reactor.core.publisher.Mono;

/**
 * YouTube Data API v3 のプロキシ（フロント互換）を提供するコントローラ。
 *
 * <p>主に再生リスト操作と検索を扱います。検索はフロントの利用形に合わせて
 * {@code channel:<channelId>} の特殊クエリを補完します。</p>
 */
@RestController
@RequestMapping("/api/youtube")
public class YoutubeController {

    private static final Pattern CHANNEL_QUERY = Pattern.compile("^\\s*channel:([A-Za-z0-9_-]{10,})\\s*$");

    private final YouTubeDataApiService youTubeDataApiService;

    public YoutubeController(YouTubeDataApiService youTubeDataApiService) {
        this.youTubeDataApiService = youTubeDataApiService;
    }

    /**
     * YouTubeの再生リスト一覧を返します。
     */
    @GetMapping("/playlists")
    Mono<Map<String, Object>> playlists(
            @RequestParam(name = "pageToken", required = false) String pageToken,
            WebSession session) {
        SessionAuth.requireUserId(session);
        String accessToken = SessionAuth.requireYouTubeAccessToken(session);
        return youTubeDataApiService
                .listPlaylists(accessToken, pageToken)
                .map(r -> Map.of(
                        "items", r.items(),
                        "nextPageToken", r.nextPageToken()));
    }

    public record CreatePlaylistRequest(String name, String description, String privacy) {
    }

    /**
     * YouTubeに再生リストを作成します。
     */
    @PostMapping("/playlists")
    Mono<Object> createPlaylist(@RequestBody Mono<CreatePlaylistRequest> body, WebSession session) {
        SessionAuth.requireUserId(session);
        String accessToken = SessionAuth.requireYouTubeAccessToken(session);
        return body.flatMap(req -> {
            if (req == null || req.name() == null || req.name().isBlank()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "name is required");
            }
            return youTubeDataApiService.createPlaylist(accessToken, req.name(), req.description(), req.privacy());
        });
    }

    public record UpdatePlaylistRequest(String name, String description, String privacy) {
    }

    /**
     * YouTube上の再生リスト情報を更新します。
     */
    @PutMapping("/playlists/{id}")
    Mono<Object> updatePlaylist(
            @PathVariable("id") String playlistId,
            @RequestBody Mono<UpdatePlaylistRequest> body,
            WebSession session) {
        SessionAuth.requireUserId(session);
        String accessToken = SessionAuth.requireYouTubeAccessToken(session);
        return body.flatMap(req -> youTubeDataApiService.updatePlaylist(
                accessToken,
                playlistId,
                req != null ? req.name() : null,
                req != null ? req.description() : null,
                req != null ? req.privacy() : null));
    }

    /**
     * YouTube上の再生リストを削除します。
     */
    @org.springframework.web.bind.annotation.DeleteMapping("/playlists/{id}")
    Mono<ResponseEntity<Void>> deletePlaylist(@PathVariable("id") String playlistId, WebSession session) {
        SessionAuth.requireUserId(session);
        String accessToken = SessionAuth.requireYouTubeAccessToken(session);
        return youTubeDataApiService
                .deletePlaylist(accessToken, playlistId)
                .thenReturn(ResponseEntity.noContent().build());
    }

    /**
     * 指定した再生リストの動画アイテム一覧を返します。
     */
    @GetMapping("/playlists/{id}/items")
    Mono<List<YouTubeDataApiService.PlaylistVideo>> playlistItems(
            @PathVariable("id") String playlistId,
            WebSession session) {
        SessionAuth.requireUserId(session);
        String accessToken = SessionAuth.requireYouTubeAccessToken(session);
        return youTubeDataApiService.listPlaylistItems(accessToken, playlistId);
    }

    public record AddVideoRequest(String videoId) {
    }

    /**
     * 指定した再生リストへ動画を追加します。
     */
    @PostMapping("/playlists/{id}/videos")
    Mono<ResponseEntity<Void>> addVideo(
            @PathVariable("id") String playlistId,
            @RequestBody Mono<AddVideoRequest> body,
            WebSession session) {
        SessionAuth.requireUserId(session);
        String accessToken = SessionAuth.requireYouTubeAccessToken(session);
        return body
                .flatMap(req -> {
                    if (req == null || req.videoId() == null || req.videoId().isBlank()) {
                        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "videoId is required");
                    }
                    return youTubeDataApiService.addVideoToPlaylist(accessToken, playlistId, req.videoId());
                })
                .thenReturn(ResponseEntity.noContent().build());
    }

    /**
     * 指定した再生リストから動画を削除します。
     */
    @org.springframework.web.bind.annotation.DeleteMapping("/playlists/{id}/videos/{videoId}")
    Mono<ResponseEntity<Void>> removeVideo(
            @PathVariable("id") String playlistId,
            @PathVariable("videoId") String videoId,
            WebSession session) {
        SessionAuth.requireUserId(session);
        String accessToken = SessionAuth.requireYouTubeAccessToken(session);
        return youTubeDataApiService
                .removeVideoFromPlaylist(accessToken, playlistId, videoId)
                .thenReturn(ResponseEntity.noContent().build());
    }

    /**
     * YouTube動画検索を行い、フロント互換の検索結果配列を返します。
     */
    @GetMapping("/search")
    Mono<List<YouTubeDataApiService.VideoSearchResult>> search(
            @RequestParam(name = "query", required = false) String query,
            @RequestParam(name = "maxResults", required = false) Integer maxResults,
            WebSession session) {
        SessionAuth.requireUserId(session);
        String accessToken = SessionAuth.requireYouTubeAccessToken(session);
        if (query == null || query.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "query が必要です");
        }

        // フロントの一部画面は `channel:<channelId>` を投げて最新動画を取ろうとする。
        // YouTube Search API では q=channel:... が有効でないことが多いので、専用ルートで補完する。
        Matcher matcher = CHANNEL_QUERY.matcher(query);
        if (matcher.matches()) {
            String channelId = matcher.group(1);
            return youTubeDataApiService
                    .fetchLatestVideoForChannel(accessToken, channelId)
                    .map(v -> List.of(new YouTubeDataApiService.VideoSearchResult(
                            v.videoId(),
                            v.title(),
                            null,
                        channelId,
                        v.thumbnailUrl(),
                        null,
                        v.publishedAt())))
                    .defaultIfEmpty(List.of());
        }

        int mr = (maxResults != null && maxResults > 0) ? Math.min(maxResults, 25) : 10;
        return youTubeDataApiService.searchVideos(accessToken, query, mr);
    }
}
