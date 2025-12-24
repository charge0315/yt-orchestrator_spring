package com.charge0315.yt.service;

import java.time.Instant;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.dao.DataAccessResourceFailureException;

import com.charge0315.yt.mongo.CachedChannel;
import com.charge0315.yt.mongo.CachedChannelRepository;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * MongoDB に保存されているチャンネルキャッシュを更新するサービス。
 *
 * <p>{@code /api/cache/refresh} から呼ばれ、登録済みチャンネルの
 * チャンネル詳細・最新動画情報を順次更新します。</p>
 */
@Service
public class CacheRefreshService {

    private final CachedChannelRepository cachedChannelRepository;

    private final YouTubeDataApiService youTubeDataApiService;

    public CacheRefreshService(CachedChannelRepository cachedChannelRepository, YouTubeDataApiService youTubeDataApiService) {
        this.cachedChannelRepository = cachedChannelRepository;
        this.youTubeDataApiService = youTubeDataApiService;
    }

    /**
     * 指定ユーザーのチャンネルキャッシュを更新し、チェック/更新件数を返します。
     */
    public Mono<RefreshResult> refreshUserCache(String userId, String accessToken) {
        if (!StringUtils.hasText(accessToken)) {
            return Mono.just(new RefreshResult(false, "youtube_access_token_missing", 0, 0));
        }

        Flux<CachedChannel> docs = cachedChannelRepository.findByUserId(userId);

        return docs
                .flatMap(doc -> refreshChannel(doc, accessToken), 3)
                .reduce(new int[] { 0, 0 }, (acc, changed) -> {
                    acc[0] += 1; // checked
                    if (changed) acc[1] += 1; // updated
                    return acc;
                })
                .map(acc -> new RefreshResult(true, null, acc[0], acc[1]))
                .onErrorResume(e -> {
                    if (isMongoUnavailable(e)) {
                        return Mono.just(new RefreshResult(false, "mongodb_not_connected", 0, 0));
                    }
                    return Mono.just(new RefreshResult(false, "internal_error", 0, 0));
                });
    }

    private static boolean isMongoUnavailable(Throwable e) {
        if (e == null) {
            return false;
        }
        if (e instanceof DataAccessResourceFailureException) {
            return true;
        }
        Throwable c = e.getCause();
        while (c != null && c != c.getCause()) {
            if (c instanceof com.mongodb.MongoTimeoutException
                    || c instanceof com.mongodb.MongoSocketOpenException
                    || c instanceof com.mongodb.MongoSocketReadException
                    || c instanceof com.mongodb.MongoSocketWriteException) {
                return true;
            }
            c = c.getCause();
        }
        return false;
    }

    private Mono<Boolean> refreshChannel(CachedChannel doc, String accessToken) {
        if (doc == null || !StringUtils.hasText(doc.getChannelId())) {
            return Mono.just(false);
        }

        Mono<YouTubeDataApiService.ChannelDetails> detailsMono = youTubeDataApiService.getChannelDetails(accessToken,
                doc.getChannelId());
        Mono<YouTubeDataApiService.LatestVideo> latestMono = youTubeDataApiService
                .fetchLatestVideoForChannel(accessToken, doc.getChannelId())
                .switchIfEmpty(Mono.just(new YouTubeDataApiService.LatestVideo(null, null, null, null)));

        return Mono.zip(detailsMono, latestMono)
                .flatMap(tuple -> {
                    YouTubeDataApiService.ChannelDetails details = tuple.getT1();
                    YouTubeDataApiService.LatestVideo latest = tuple.getT2();

                    boolean changedByDetails = false;
                    if (StringUtils.hasText(details.subscriberCount())
                            && (doc.getSubscriberCount() == null || doc.getSubscriberCount().isBlank())) {
                        doc.setSubscriberCount(details.subscriberCount());
                        changedByDetails = true;
                    }

                    if (!StringUtils.hasText(latest.videoId())) {
                        // 最新動画が取れない場合は更新しない（既存値は保持）
                        if (changedByDetails) {
                            doc.setUpdatedAt(Instant.now());
                            return cachedChannelRepository.save(doc).thenReturn(true);
                        }
                        return Mono.just(false);
                    }

                    boolean isNewVideo = doc.getLatestVideoId() == null || !doc.getLatestVideoId().equals(latest.videoId());
                    boolean needsVideoDetails = isNewVideo
                            || doc.getLatestVideoDuration() == null
                            || doc.getLatestVideoDuration().isBlank()
                            || doc.getLatestVideoViewCount() == null;

                    Mono<YouTubeDataApiService.VideoDetails> videoDetailsMono = needsVideoDetails
                            ? youTubeDataApiService.getVideoDetails(accessToken, latest.videoId())
                                    .switchIfEmpty(Mono.just(new YouTubeDataApiService.VideoDetails(null, null)))
                            : Mono.just(new YouTubeDataApiService.VideoDetails(doc.getLatestVideoDuration(), doc.getLatestVideoViewCount()));

                    final boolean detailsChangedFinal = changedByDetails;

                    return videoDetailsMono.flatMap(videoDetails -> {
                        boolean changed = detailsChangedFinal;

                        if (isNewVideo) {
                            doc.setLatestVideoId(latest.videoId());
                            doc.setLatestVideoTitle(latest.title());
                            doc.setLatestVideoThumbnail(latest.thumbnailUrl());
                            doc.setLatestVideoPublishedAt(parseInstant(latest.publishedAt()));
                            doc.setLatestVideoDuration(videoDetails.duration());
                            doc.setLatestVideoViewCount(videoDetails.viewCount());
                            changed = true;
                        } else {
                            if ((doc.getLatestVideoDuration() == null || doc.getLatestVideoDuration().isBlank())
                                    && StringUtils.hasText(videoDetails.duration())) {
                                doc.setLatestVideoDuration(videoDetails.duration());
                                changed = true;
                            }
                            if (doc.getLatestVideoViewCount() == null && videoDetails.viewCount() != null) {
                                doc.setLatestVideoViewCount(videoDetails.viewCount());
                                changed = true;
                            }
                        }

                        if (!changed) {
                            return Mono.just(false);
                        }
                        doc.setUpdatedAt(Instant.now());
                        return cachedChannelRepository.save(doc).thenReturn(true);
                    });
                })
                // refresh は失敗しても全体を止めない
                .onErrorResume(e -> Mono.just(false));
    }

    private static Instant parseInstant(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Instant.parse(value);
        } catch (Exception e) {
            return null;
        }
    }

    public record RefreshResult(boolean ok, String error, int checked, int updated) {
    }
}
