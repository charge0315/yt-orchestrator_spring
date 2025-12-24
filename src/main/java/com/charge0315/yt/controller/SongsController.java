package com.charge0315.yt.controller;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.WebSession;

import com.charge0315.yt.service.YouTubeDataApiService;
import com.charge0315.yt.util.SessionAuth;

import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/songs")
public class SongsController {

    private final YouTubeDataApiService youTubeDataApiService;

    public SongsController(YouTubeDataApiService youTubeDataApiService) {
        this.youTubeDataApiService = youTubeDataApiService;
    }

    @GetMapping("/search")
    Mono<List<Map<String, Object>>> search(@RequestParam(name = "query", required = false) String query, WebSession session) {
        SessionAuth.requireUserId(session);
        String accessToken = SessionAuth.requireYouTubeAccessToken(session);
        if (query == null || query.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "query パラメータが必要です");
        }

        return youTubeDataApiService
            .searchVideos(accessToken, query, 25)
            .map(items -> items.stream()
                .map(v -> Map.<String, Object>of(
                    "videoId", v.videoId(),
                    "title", v.title(),
                    "artist", v.channelTitle() != null ? v.channelTitle() : "不明なアーティスト",
                    "thumbnail", v.thumbnail()))
                .collect(Collectors.toList()))
            ;
    }
}
