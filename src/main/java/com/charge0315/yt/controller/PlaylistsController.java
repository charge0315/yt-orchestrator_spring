package com.charge0315.yt.controller;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.WebSession;
import org.springframework.web.server.ResponseStatusException;

import com.charge0315.yt.util.SessionAuth;
import com.charge0315.yt.service.YouTubeDataApiService;

import reactor.core.publisher.Mono;

/**
 * プレイリスト（主に YouTube 側）に関するAPI。
 *
 * <p>エクスポート/インポートに加えて、フロント互換の
 * {@code /api/playlists/{id}}（name/songs 返却）や曲追加/削除を提供します。</p>
 */
@RestController
@RequestMapping("/api/playlists")
public class PlaylistsController {

    private final YouTubeDataApiService youTubeDataApiService;

    public PlaylistsController(YouTubeDataApiService youTubeDataApiService) {
        this.youTubeDataApiService = youTubeDataApiService;
    }

    @GetMapping
    Mono<Map<String, Object>> list(WebSession session) {
        SessionAuth.requireUserId(session);
        String accessToken = SessionAuth.requireYouTubeAccessToken(session);
        return youTubeDataApiService
                .listPlaylists(accessToken, null)
                .map(r -> Map.of(
                        "items", r.items(),
                        "nextPageToken", r.nextPageToken()));
    }

    /**
     * Frontend 互換: `GET /api/playlists/{id}`
     *
     * フロントの PlaylistDetailPage は `/api/playlists/:id` を叩いて `name` / `songs[]` を表示する。
     * 現状の Spring は export/import 用だったため、YouTube Data API を使って詳細を返す。
     */
    @GetMapping("/{id}")
    Mono<ResponseEntity<Map<String, Object>>> detail(@PathVariable("id") String id, WebSession session) {
        SessionAuth.requireUserId(session);
        String accessToken = SessionAuth.requireYouTubeAccessToken(session);
        if (id == null || id.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "id_required");
        }

        Mono<com.fasterxml.jackson.databind.JsonNode> playlistMono = youTubeDataApiService.getPlaylist(accessToken, id);
        Mono<List<YouTubeDataApiService.PlaylistVideo>> itemsMono = youTubeDataApiService.listPlaylistItems(accessToken, id);

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
                    "id", playlist.path("id").asText(id),
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

    public record AddSongRequest(String videoId) {
    }

    /**
     * Frontend 互換: `POST /api/playlists/{id}/songs`
     */
    @PostMapping("/{id}/songs")
    Mono<Map<String, Object>> addSong(
            @PathVariable("id") String playlistId,
            @RequestBody Mono<AddSongRequest> body,
            WebSession session) {
        SessionAuth.requireUserId(session);
        String accessToken = SessionAuth.requireYouTubeAccessToken(session);
        if (playlistId == null || playlistId.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "id_required");
        }
        return body.flatMap(req -> {
            if (req == null || req.videoId() == null || req.videoId().isBlank()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "videoId_required");
            }
            return youTubeDataApiService.addVideoToPlaylist(accessToken, playlistId, req.videoId())
                .thenReturn(Map.of("ok", true));
        });
    }

    /**
     * Frontend 互換: `DELETE /api/playlists/{id}/songs/{videoId}`
     */
    @DeleteMapping("/{id}/songs/{videoId}")
    Mono<Map<String, Object>> removeSong(
            @PathVariable("id") String playlistId,
            @PathVariable("videoId") String videoId,
            WebSession session) {
        SessionAuth.requireUserId(session);
        String accessToken = SessionAuth.requireYouTubeAccessToken(session);
        if (playlistId == null || playlistId.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "id_required");
        }
        if (videoId == null || videoId.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "videoId_required");
        }
        return youTubeDataApiService
            .removeVideoFromPlaylist(accessToken, playlistId, videoId)
            .thenReturn(Map.of("ok", true));
    }

    @GetMapping("/{id}/export")
    Mono<ResponseEntity<Map<String, Object>>> export(@PathVariable("id") String id, WebSession session) {
        SessionAuth.requireUserId(session);
        String accessToken = SessionAuth.requireYouTubeAccessToken(session);
        if (id == null || id.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "id_required");
        }

        Mono<com.fasterxml.jackson.databind.JsonNode> playlistMono = youTubeDataApiService.getPlaylist(accessToken, id);
        Mono<List<YouTubeDataApiService.PlaylistVideo>> itemsMono = youTubeDataApiService.listPlaylistItems(accessToken,
                id);

        return Mono.zip(playlistMono, itemsMono)
                .map(tuple -> {
                    com.fasterxml.jackson.databind.JsonNode playlist = tuple.getT1();
                    List<YouTubeDataApiService.PlaylistVideo> items = tuple.getT2();

                    Map<String, Object> out = Map.of(
                            "playlistId", playlist.path("id").asText(id),
                            "title", playlist.path("snippet").path("title").asText(""),
                            "description", playlist.path("snippet").path("description").asText(""),
                            "items", items.stream().map(v -> Map.of(
                                    "videoId", v.videoId(),
                                    "title", v.title(),
                                    "channelTitle", v.channelTitle(),
                                    "thumbnail", v.thumbnail(),
                                    "duration", v.duration(),
                                    "publishedAt", v.publishedAt()))
                                    .collect(Collectors.toList()));

                        return ResponseEntity
                            .ok()
                            .contentType(MediaType.APPLICATION_JSON)
                            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=playlist_" + id + ".json")
                            .body(out);
                });
    }

    public record ImportRequest(
            String playlistId,
            String name,
            String title,
            String description,
            List<Map<String, Object>> items,
            List<Map<String, Object>> videos,
            List<String> videoIds) {
    }

    @PostMapping("/import")
    Mono<Map<String, Object>> importPlaylist(@RequestBody Mono<ImportRequest> body, WebSession session) {
        SessionAuth.requireUserId(session);
        String accessToken = SessionAuth.requireYouTubeAccessToken(session);

        return body.flatMap(req -> {
            if (req == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "body_required");
            }

            String targetPlaylistId = req.playlistId();
            String playlistName = (req.name() != null && !req.name().isBlank()) ? req.name() : req.title();
            if (targetPlaylistId == null || targetPlaylistId.isBlank()) {
                if (playlistName == null || playlistName.isBlank()) {
                    playlistName = "Imported Playlist";
                }
            }

            List<String> ids = new ArrayList<>();
            if (req.videoIds() != null) {
                ids.addAll(req.videoIds().stream().filter(s -> s != null && !s.isBlank()).toList());
            }
            if (req.items() != null) {
                for (Map<String, Object> it : req.items()) {
                    Object vid = it != null ? it.get("videoId") : null;
                    if (vid != null && !vid.toString().isBlank()) {
                        ids.add(vid.toString());
                    }
                }
            }
            if (req.videos() != null) {
                for (Map<String, Object> it : req.videos()) {
                    Object vid = it != null ? it.get("videoId") : null;
                    if (vid != null && !vid.toString().isBlank()) {
                        ids.add(vid.toString());
                    }
                }
            }

            int total = ids.size();

            Mono<String> playlistIdMono = (targetPlaylistId != null && !targetPlaylistId.isBlank())
                    ? Mono.just(targetPlaylistId)
                    : youTubeDataApiService
                            .createPlaylist(accessToken, playlistName, req.description(), "private")
                            .map(json -> json.path("id").asText(null))
                            .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                                    "playlist_create_failed")));

            return playlistIdMono.flatMap(pid -> {
                // 追加は逐次（クォータ/順序/失敗時挙動を単純化）
                return reactor.core.publisher.Flux.fromIterable(ids)
                        .concatMap(videoId -> youTubeDataApiService.addVideoToPlaylist(accessToken, pid, videoId)
                                .thenReturn(1)
                                .onErrorResume(e -> Mono.just(0)))
                        .reduce(0, Integer::sum)
                        .map(added -> Map.<String, Object>of(
                                "ok", true,
                                "playlistId", pid,
                                "stats", Map.of(
                                        "added", added,
                                        "total", total)));
            });
        });
    }
}
