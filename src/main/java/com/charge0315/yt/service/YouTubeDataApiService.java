package com.charge0315.yt.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.UriComponentsBuilder;

import com.fasterxml.jackson.databind.JsonNode;

import reactor.core.publisher.Mono;

@Service
public class YouTubeDataApiService {

    private final WebClient webClient;

    public YouTubeDataApiService(WebClient.Builder builder) {
        this.webClient = builder
                .baseUrl("https://www.googleapis.com/youtube/v3")
                .build();
    }

    public Mono<List<VideoSearchResult>> searchVideos(String accessToken, String query, int maxResults) {
        return webClient
                .get()
                .uri(uriBuilder -> UriComponentsBuilder
                        .fromPath("/search")
                        .queryParam("part", "snippet")
                        .queryParam("type", "video")
                        .queryParam("order", "relevance")
                        .queryParam("maxResults", maxResults)
                .queryParam("q", query)
                        .build(true)
                        .toUri())
                .headers(h -> h.setBearerAuth(accessToken))
                .retrieve()
                .bodyToMono(JsonNode.class)
                .flatMap(json -> {
                    List<VideoSearchResult> base = new ArrayList<>();
                    List<String> ids = new ArrayList<>();

                    JsonNode items = json.get("items");
                    if (items != null && items.isArray()) {
                        for (JsonNode item : items) {
                            String videoId = item.path("id").path("videoId").asText(null);
                            String title = item.path("snippet").path("title").asText(null);
                            String channelTitle = item.path("snippet").path("channelTitle").asText(null);
                            String channelId = item.path("snippet").path("channelId").asText(null);
                            String publishedAt = item.path("snippet").path("publishedAt").asText(null);
                            String thumbnail = item.path("snippet").path("thumbnails").path("default").path("url").asText(null);
                            if (thumbnail == null) {
                                thumbnail = item.path("snippet").path("thumbnails").path("medium").path("url").asText(null);
                            }
                            if (videoId != null) {
                                base.add(new VideoSearchResult(videoId, title, channelTitle, channelId, thumbnail, null, publishedAt));
                                ids.add(videoId);
                            }
                        }
                    }

                    if (ids.isEmpty()) {
                        return Mono.just(base);
                    }

                    return fetchDurations(accessToken, ids)
                            .map(durationMap -> base.stream()
                                    .map(v -> new VideoSearchResult(
                                            v.videoId(),
                                            v.title(),
                                            v.channelTitle(),
                                            v.channelId(),
                                            v.thumbnail(),
                                    durationMap.get(v.videoId()),
                                    v.publishedAt()))
                                    .toList());
                });
    }

    public Mono<List<ChannelSearchResult>> searchChannels(String accessToken, String query, int maxResults) {
        return webClient
                .get()
                .uri(uriBuilder -> UriComponentsBuilder
                        .fromPath("/search")
                        .queryParam("part", "snippet")
                        .queryParam("type", "channel")
                        .queryParam("order", "relevance")
                        .queryParam("maxResults", maxResults)
                        .queryParam("q", query)
                        .build(true)
                        .toUri())
                .headers(h -> h.setBearerAuth(accessToken))
                .retrieve()
                .bodyToMono(JsonNode.class)
                .map(json -> {
                    List<ChannelSearchResult> out = new ArrayList<>();
                    JsonNode items = json.get("items");
                    if (items != null && items.isArray()) {
                        for (JsonNode item : items) {
                            String channelId = item.path("id").path("channelId").asText(null);
                            String title = item.path("snippet").path("title").asText(null);
                            String description = item.path("snippet").path("description").asText(null);
                            String thumbnail = item.path("snippet").path("thumbnails").path("default").path("url").asText(null);
                            if (thumbnail == null) {
                                thumbnail = item.path("snippet").path("thumbnails").path("medium").path("url").asText(null);
                            }
                            if (thumbnail == null) {
                                thumbnail = item.path("snippet").path("thumbnails").path("high").path("url").asText(null);
                            }
                            if (channelId != null) {
                                out.add(new ChannelSearchResult(channelId, title, description, thumbnail));
                            }
                        }
                    }
                    return out;
                });
    }

    public Mono<ChannelDetails> getChannelDetails(String accessToken, String channelId) {
        return webClient
                .get()
                .uri(uriBuilder -> UriComponentsBuilder
                        .fromPath("/channels")
                        .queryParam("part", "snippet,statistics")
                        .queryParam("id", channelId)
                        .queryParam("maxResults", 1)
                        .build(true)
                        .toUri())
                .headers(h -> h.setBearerAuth(accessToken))
                .retrieve()
                .bodyToMono(JsonNode.class)
                .map(json -> {
                    JsonNode items = json.get("items");
                    if (items == null || !items.isArray() || items.isEmpty()) {
                        throw new org.springframework.web.server.ResponseStatusException(
                                org.springframework.http.HttpStatus.NOT_FOUND, "channel_not_found");
                    }
                    JsonNode item = items.get(0);
                    String id = item.path("id").asText(null);
                    String title = item.path("snippet").path("title").asText(null);
                    String description = item.path("snippet").path("description").asText(null);
                    String thumbnail = item.path("snippet").path("thumbnails").path("default").path("url").asText(null);
                    if (thumbnail == null) {
                        thumbnail = item.path("snippet").path("thumbnails").path("medium").path("url").asText(null);
                    }
                    if (thumbnail == null) {
                        thumbnail = item.path("snippet").path("thumbnails").path("high").path("url").asText(null);
                    }
                    String subscriberCount = item.path("statistics").path("subscriberCount").asText(null);
                    return new ChannelDetails(id, title, description, thumbnail, subscriberCount);
                });
    }

    public Mono<LatestVideo> fetchLatestVideoForChannel(String accessToken, String channelId) {
        return webClient
                .get()
                .uri(uriBuilder -> UriComponentsBuilder
                        .fromPath("/search")
                        .queryParam("part", "snippet")
                        .queryParam("type", "video")
                        .queryParam("order", "date")
                        .queryParam("maxResults", 1)
                        .queryParam("channelId", channelId)
                        .build(true)
                        .toUri())
                .headers(h -> h.setBearerAuth(accessToken))
                .retrieve()
                .bodyToMono(JsonNode.class)
                .flatMap(json -> {
                    JsonNode items = json.get("items");
                    if (items == null || !items.isArray() || items.isEmpty()) {
                        return Mono.empty();
                    }
                    JsonNode item = items.get(0);
                    String videoId = item.path("id").path("videoId").asText(null);
                    String title = item.path("snippet").path("title").asText(null);
                    String publishedAt = item.path("snippet").path("publishedAt").asText(null);
                    String thumbnail = item.path("snippet").path("thumbnails").path("medium").path("url").asText(null);
                    if (thumbnail == null) {
                        thumbnail = item.path("snippet").path("thumbnails").path("default").path("url").asText(null);
                    }
                    if (videoId == null) {
                        return Mono.empty();
                    }
                    return Mono.just(new LatestVideo(videoId, title, thumbnail, publishedAt));
                });
    }

    public Mono<VideoDetails> getVideoDetails(String accessToken, String videoId) {
        if (!StringUtils.hasText(videoId)) {
            return Mono.empty();
        }

        return webClient
                .get()
                .uri(uriBuilder -> UriComponentsBuilder
                        .fromPath("/videos")
                        .queryParam("part", "contentDetails,statistics")
                        .queryParam("id", videoId)
                        .queryParam("maxResults", 1)
                        .build(true)
                        .toUri())
                .headers(h -> h.setBearerAuth(accessToken))
                .retrieve()
                .bodyToMono(JsonNode.class)
                .flatMap(json -> {
                    JsonNode items = json.get("items");
                    if (items == null || !items.isArray() || items.isEmpty()) {
                        return Mono.empty();
                    }
                    JsonNode item = items.get(0);
                    String duration = item.path("contentDetails").path("duration").asText(null);
                    Long viewCount = item.path("statistics").path("viewCount").isMissingNode()
                            ? null
                            : item.path("statistics").path("viewCount").asLong();
                    return Mono.just(new VideoDetails(duration, viewCount));
                });
    }

    public Mono<PlaylistsResponse> listPlaylists(String accessToken, String pageToken) {
        return webClient
                .get()
                .uri(uriBuilder -> UriComponentsBuilder
                        .fromPath("/playlists")
                        .queryParam("part", "snippet,contentDetails,status")
                        .queryParam("mine", "true")
                        .queryParam("maxResults", 25)
                        .queryParamIfPresent("pageToken", pageToken == null || pageToken.isBlank()
                                ? java.util.Optional.empty()
                                : java.util.Optional.of(pageToken))
                        .build(true)
                        .toUri())
                .headers(h -> h.setBearerAuth(accessToken))
                .retrieve()
                .bodyToMono(JsonNode.class)
                .map(json -> {
                    List<Object> items = new ArrayList<>();
                    JsonNode rawItems = json.get("items");
                    if (rawItems != null && rawItems.isArray()) {
                        for (JsonNode item : rawItems) {
                            // フロントは YouTube API 風の構造（id/snippet/contentDetails）を参照する
                            items.add(item);
                        }
                    }
                    String next = json.hasNonNull("nextPageToken") ? json.get("nextPageToken").asText() : null;
                    return new PlaylistsResponse(items, next);
                });
    }

    public Mono<JsonNode> getPlaylist(String accessToken, String playlistId) {
        return webClient
                .get()
                .uri(uriBuilder -> UriComponentsBuilder
                        .fromPath("/playlists")
                        .queryParam("part", "snippet,contentDetails,status")
                        .queryParam("id", playlistId)
                        .queryParam("maxResults", 1)
                        .build(true)
                        .toUri())
                .headers(h -> h.setBearerAuth(accessToken))
                .retrieve()
                .bodyToMono(JsonNode.class)
                .map(json -> {
                    JsonNode items = json.get("items");
                    if (items == null || !items.isArray() || items.isEmpty()) {
                        throw new org.springframework.web.server.ResponseStatusException(
                                org.springframework.http.HttpStatus.NOT_FOUND, "playlist_not_found");
                    }
                    return items.get(0);
                });
    }

        public record ChannelDetails(String channelId, String title, String description, String thumbnailUrl,
            String subscriberCount) {
        }

        public record LatestVideo(String videoId, String title, String thumbnailUrl, String publishedAt) {
        }

        public record VideoDetails(String duration, Long viewCount) {
        }

    public Mono<JsonNode> createPlaylist(String accessToken, String name, String description, String privacyStatus) {
        Map<String, Object> snippet = new HashMap<>();
        snippet.put("title", name);
        if (StringUtils.hasText(description)) {
            snippet.put("description", description);
        }

        Map<String, Object> status = new HashMap<>();
        status.put("privacyStatus", StringUtils.hasText(privacyStatus) ? privacyStatus : "private");

        Map<String, Object> body = Map.of(
                "snippet", snippet,
                "status", status);

        return webClient
                .post()
                .uri(uriBuilder -> UriComponentsBuilder
                        .fromPath("/playlists")
                        .queryParam("part", "snippet,contentDetails,status")
                        .build(true)
                        .toUri())
                .headers(h -> h.setBearerAuth(accessToken))
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .retrieve()
                .bodyToMono(JsonNode.class);
    }

    public Mono<JsonNode> updatePlaylist(String accessToken, String playlistId, String name, String description, String privacyStatus) {
        Map<String, Object> snippet = new HashMap<>();
        if (StringUtils.hasText(name)) {
            snippet.put("title", name);
        }
        if (StringUtils.hasText(description)) {
            snippet.put("description", description);
        }

        Map<String, Object> body = new HashMap<>();
        body.put("id", playlistId);
        if (!snippet.isEmpty()) {
            body.put("snippet", snippet);
        }
        if (StringUtils.hasText(privacyStatus)) {
            body.put("status", Map.of("privacyStatus", privacyStatus));
        }

        return webClient
                .put()
                .uri(uriBuilder -> UriComponentsBuilder
                        .fromPath("/playlists")
                        .queryParam("part", "snippet,contentDetails,status")
                        .build(true)
                        .toUri())
                .headers(h -> h.setBearerAuth(accessToken))
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .retrieve()
                .bodyToMono(JsonNode.class);
    }

    public Mono<Void> deletePlaylist(String accessToken, String playlistId) {
        return webClient
                .delete()
                .uri(uriBuilder -> UriComponentsBuilder
                        .fromPath("/playlists")
                        .queryParam("id", playlistId)
                        .build(true)
                        .toUri())
                .headers(h -> h.setBearerAuth(accessToken))
                .retrieve()
                .bodyToMono(Void.class);
    }

    public Mono<List<PlaylistVideo>> listPlaylistItems(String accessToken, String playlistId) {
        return webClient
                .get()
                .uri(uriBuilder -> UriComponentsBuilder
                        .fromPath("/playlistItems")
                        .queryParam("part", "snippet,contentDetails")
                        .queryParam("playlistId", playlistId)
                        .queryParam("maxResults", 50)
                        .build(true)
                        .toUri())
                .headers(h -> h.setBearerAuth(accessToken))
                .retrieve()
                .bodyToMono(JsonNode.class)
                .flatMap(json -> {
                    List<PlaylistVideo> videos = new ArrayList<>();
                    Set<String> videoIds = new HashSet<>();
                    JsonNode items = json.get("items");
                    if (items != null && items.isArray()) {
                        for (JsonNode item : items) {
                            String vid = item.path("snippet").path("resourceId").path("videoId").asText(null);
                            if (vid == null) {
                                vid = item.path("contentDetails").path("videoId").asText(null);
                            }
                            if (vid == null) {
                                continue;
                            }

                            String title = item.path("snippet").path("title").asText(null);
                            String channelTitle = item.path("snippet").path("videoOwnerChannelTitle").asText(null);
                            if (channelTitle == null) {
                                channelTitle = item.path("snippet").path("channelTitle").asText(null);
                            }
                            String thumbnail = item.path("snippet").path("thumbnails").path("default").path("url").asText(null);
                            if (thumbnail == null) {
                                thumbnail = item.path("snippet").path("thumbnails").path("medium").path("url").asText(null);
                            }
                            String publishedAt = item.path("snippet").path("publishedAt").asText(null);

                            videos.add(new PlaylistVideo(vid, title, channelTitle, thumbnail, null, publishedAt));
                            videoIds.add(vid);
                        }
                    }

                    if (videoIds.isEmpty()) {
                        return Mono.just(videos);
                    }

                    return fetchDurations(accessToken, new ArrayList<>(videoIds))
                            .map(durations -> videos.stream()
                                    .map(v -> new PlaylistVideo(
                                            v.videoId(),
                                            v.title(),
                                            v.channelTitle(),
                                            v.thumbnail(),
                                            durations.get(v.videoId()),
                                            v.publishedAt()))
                                    .collect(Collectors.toList()));
                });
    }

    public Mono<Void> addVideoToPlaylist(String accessToken, String playlistId, String videoId) {
        Map<String, Object> body = Map.of(
                "snippet", Map.of(
                        "playlistId", playlistId,
                        "resourceId", Map.of(
                                "kind", "youtube#video",
                                "videoId", videoId)));

        return webClient
                .post()
                .uri(uriBuilder -> UriComponentsBuilder
                        .fromPath("/playlistItems")
                        .queryParam("part", "snippet")
                        .build(true)
                        .toUri())
                .headers(h -> h.setBearerAuth(accessToken))
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .bodyValue(body)
                .retrieve()
                .bodyToMono(JsonNode.class)
                .then();
    }

    public Mono<Void> removeVideoFromPlaylist(String accessToken, String playlistId, String videoId) {
        // playlistItemId が必要なので、playlistItems から videoId に一致するものを探して削除
        return webClient
                .get()
                .uri(uriBuilder -> UriComponentsBuilder
                        .fromPath("/playlistItems")
                        .queryParam("part", "snippet")
                        .queryParam("playlistId", playlistId)
                        .queryParam("maxResults", 50)
                        .build(true)
                        .toUri())
                .headers(h -> h.setBearerAuth(accessToken))
                .retrieve()
                .bodyToMono(JsonNode.class)
                .flatMap(json -> {
                    String playlistItemId = null;
                    JsonNode items = json.get("items");
                    if (items != null && items.isArray()) {
                        for (JsonNode item : items) {
                            String vid = item.path("snippet").path("resourceId").path("videoId").asText(null);
                            if (videoId.equals(vid)) {
                                playlistItemId = item.path("id").asText(null);
                                break;
                            }
                        }
                    }

                    if (playlistItemId == null) {
                        return Mono.empty();
                    }

                    final String idToDelete = playlistItemId;

                    return webClient
                            .delete()
                            .uri(uriBuilder -> UriComponentsBuilder
                                    .fromPath("/playlistItems")
                                    .queryParam("id", idToDelete)
                                    .build(true)
                                    .toUri())
                            .headers(h -> h.setBearerAuth(accessToken))
                            .retrieve()
                            .bodyToMono(Void.class);
                })
                .then();
    }

    private Mono<Map<String, String>> fetchDurations(String accessToken, List<String> videoIds) {
        // videos.list は最大50
        List<String> ids = videoIds.size() > 50 ? videoIds.subList(0, 50) : videoIds;

        return webClient
                .get()
                .uri(uriBuilder -> UriComponentsBuilder
                        .fromPath("/videos")
                        .queryParam("part", "contentDetails")
                        .queryParam("id", String.join(",", ids))
                        .build(true)
                        .toUri())
                .headers(h -> h.setBearerAuth(accessToken))
                .retrieve()
                .bodyToMono(JsonNode.class)
                .map(json -> {
                    Map<String, String> out = new HashMap<>();
                    JsonNode items = json.get("items");
                    if (items != null && items.isArray()) {
                        for (JsonNode item : items) {
                            String id = item.path("id").asText(null);
                            String duration = item.path("contentDetails").path("duration").asText(null);
                            if (id != null) {
                                out.put(id, duration);
                            }
                        }
                    }
                    return out;
                });
    }

        public record VideoSearchResult(
            String videoId,
            String title,
            String channelTitle,
            String channelId,
            String thumbnail,
                String duration,
                String publishedAt) {
    }

    public record ChannelSearchResult(String channelId, String title, String description, String thumbnailUrl) {
    }

    public record PlaylistsResponse(List<Object> items, String nextPageToken) {
    }

    public record PlaylistVideo(
            String videoId,
            String title,
            String channelTitle,
            String thumbnail,
            String duration,
            String publishedAt) {
    }
}
