package com.charge0315.yt.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.WebSession;

import com.charge0315.yt.service.RecommendationsService;
import com.charge0315.yt.service.YouTubeDataApiService;
import com.charge0315.yt.service.YouTubeChannelsService;
import com.charge0315.yt.util.SessionAuth;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * YouTube関連のおすすめAPI（フロント互換）。
 *
 * <p>AIおすすめ結果（検索キーワード）を元に、YouTubeのチャンネル候補や
 * 登録チャンネルの最新動画リストを返します。</p>
 */
@RestController
@RequestMapping("/api/youtube/recommendations")
public class YoutubeRecommendationsController {

    private final RecommendationsService recommendationsService;
    private final YouTubeDataApiService youTubeDataApiService;
    private final YouTubeChannelsService youTubeChannelsService;

    public YoutubeRecommendationsController(
            RecommendationsService recommendationsService,
            YouTubeDataApiService youTubeDataApiService,
            YouTubeChannelsService youTubeChannelsService) {
        this.recommendationsService = recommendationsService;
        this.youTubeDataApiService = youTubeDataApiService;
        this.youTubeChannelsService = youTubeChannelsService;
    }

    public record ChannelRecommendation(
            String channelId,
            String name,
            String thumbnail,
            String subscriberCount,
            String description,
            String reason) {
    }

    @GetMapping("/channels")
    Mono<List<ChannelRecommendation>> channels(WebSession session) {
        String userId = SessionAuth.requireUserId(session);
        String accessToken = SessionAuth.requireYouTubeAccessToken(session);

        return recommendationsService
            .getRecommendations(userId)
            .flatMapMany(Flux::fromIterable)
            .concatMap(entry -> youTubeDataApiService
                .searchChannels(accessToken, entry.channelTitle(), 1)
                .flatMapMany(list -> list == null || list.isEmpty()
                    ? Flux.empty()
                    : Flux.just(list.get(0)))
                .flatMap(search -> youTubeDataApiService
                    .getChannelDetails(accessToken, search.channelId())
                    .onErrorResume(e -> Mono.just(new YouTubeDataApiService.ChannelDetails(
                        search.channelId(),
                        search.title(),
                        search.description(),
                        search.thumbnailUrl(),
                        null))))
                .map(details -> new ChannelRecommendation(
                    details.channelId(),
                    details.title(),
                    details.thumbnailUrl(),
                    details.subscriberCount(),
                    details.description(),
                    entry.reason())))
            .take(5)
            .collectList();
    }

    @GetMapping("/videos")
    Mono<List<YouTubeChannelsService.LatestVideo>> videos(WebSession session) {
        String userId = SessionAuth.requireUserId(session);
        String accessToken = SessionAuth.getYouTubeAccessTokenOrNull(session);
        return youTubeChannelsService.getLatestVideos(userId, accessToken);
    }
}
