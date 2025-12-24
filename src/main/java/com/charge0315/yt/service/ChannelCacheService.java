package com.charge0315.yt.service;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.charge0315.yt.mongo.CachedChannel;
import com.charge0315.yt.mongo.CachedChannelRepository;

import reactor.core.publisher.Mono;
import reactor.core.publisher.Flux;

@Service
public class ChannelCacheService {

    private final YouTubeDataApiService youTubeDataApiService;

    private final CachedChannelRepository cachedChannelRepository;

    public ChannelCacheService(YouTubeDataApiService youTubeDataApiService, CachedChannelRepository cachedChannelRepository) {
        this.youTubeDataApiService = youTubeDataApiService;
        this.cachedChannelRepository = cachedChannelRepository;
    }

    public Mono<List<CachedChannelEntry>> listChannels(String userId) {
        return cachedChannelRepository
                .findByUserIdAndIsArtistFalseOrderByChannelTitleAsc(userId)
                .map(ChannelCacheService::toEntry)
                .collectList();
    }

    public Mono<List<CachedChannelEntry>> listArtists(String userId) {
        return cachedChannelRepository
                .findByUserIdAndIsArtistTrueOrderByChannelTitleAsc(userId)
                .map(ChannelCacheService::toEntry)
                .collectList();
    }

    public Mono<CachedChannelEntry> subscribe(String userId, String accessToken, String channelId) {
        return cachedChannelRepository
                .findFirstByUserIdAndChannelId(userId, channelId)
                .map(ChannelCacheService::toEntry)
                .switchIfEmpty(createAndSave(userId, accessToken, channelId));
    }

    private Mono<CachedChannelEntry> createAndSave(String userId, String accessToken, String channelId) {
        Mono<YouTubeDataApiService.ChannelDetails> detailsMono = youTubeDataApiService.getChannelDetails(accessToken, channelId);
        Mono<YouTubeDataApiService.LatestVideo> latestMono = youTubeDataApiService
                .fetchLatestVideoForChannel(accessToken, channelId)
                .switchIfEmpty(Mono.just(new YouTubeDataApiService.LatestVideo(null, null, null, null)));

        return Mono.zip(detailsMono, latestMono)
                .flatMap(tuple -> {
                    YouTubeDataApiService.ChannelDetails details = tuple.getT1();
                    YouTubeDataApiService.LatestVideo latest = tuple.getT2();

                    Mono<YouTubeDataApiService.VideoDetails> videoDetailsMono = (latest.videoId() != null
                            && !latest.videoId().isBlank())
                                    ? youTubeDataApiService.getVideoDetails(accessToken, latest.videoId())
                                            .switchIfEmpty(Mono.just(new YouTubeDataApiService.VideoDetails(null, null)))
                                    : Mono.just(new YouTubeDataApiService.VideoDetails(null, null));

                    return videoDetailsMono.flatMap(videoDetails -> {
                        CachedChannel doc = new CachedChannel();
                        doc.setId(UUID.randomUUID().toString());
                        doc.setUserId(userId);
                        doc.setChannelId(details.channelId());
                        doc.setChannelTitle(details.title());
                        doc.setChannelDescription(details.description());
                        doc.setThumbnailUrl(details.thumbnailUrl());
                        doc.setSubscriberCount(details.subscriberCount());
                        doc.setIsArtist(false);
                        doc.setVideoCount(0);

                        if (latest.videoId() != null && !latest.videoId().isBlank()) {
                            doc.setLatestVideoId(latest.videoId());
                            doc.setLatestVideoTitle(latest.title());
                            doc.setLatestVideoThumbnail(latest.thumbnailUrl());
                            doc.setLatestVideoPublishedAt(parseInstant(latest.publishedAt()));
                            doc.setLatestVideoDuration(videoDetails.duration());
                            doc.setLatestVideoViewCount(videoDetails.viewCount());
                        }

                        java.time.Instant now = java.time.Instant.now();
                        doc.setCreatedAt(now);
                        doc.setUpdatedAt(now);
                        return cachedChannelRepository.save(doc);
                    });
                })
                .map(ChannelCacheService::toEntry);
    }

    public Mono<Void> unsubscribe(String userId, String idOrChannelId) {
        // まず subscriptionId として削除を試す。なければ channelId として削除。
        return cachedChannelRepository
                .deleteByUserIdAndId(userId, idOrChannelId)
                .flatMap(deleted -> deleted != null && deleted > 0
                        ? Mono.<Void>empty()
                        : cachedChannelRepository.deleteByUserIdAndChannelId(userId, idOrChannelId).then());
    }

    public Mono<Void> setArtistFlag(String userId, String channelId, boolean isArtist) {
        return cachedChannelRepository
                .findFirstByUserIdAndChannelId(userId, channelId)
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND, "not_found")))
                .flatMap(doc -> {
                    doc.setIsArtist(isArtist);
                    doc.setUpdatedAt(java.time.Instant.now());
                    return cachedChannelRepository.save(doc);
                })
                .then();
    }

    public Mono<Void> setArtistFlagById(String userId, String idOrChannelId, boolean isArtist) {
        return cachedChannelRepository
                .findById(idOrChannelId)
                .filter(doc -> userId.equals(doc.getUserId()))
                .switchIfEmpty(cachedChannelRepository.findFirstByUserIdAndChannelId(userId, idOrChannelId))
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND, "not_found")))
                .flatMap(doc -> {
                    doc.setIsArtist(isArtist);
                    doc.setUpdatedAt(java.time.Instant.now());
                    return cachedChannelRepository.save(doc);
                })
                .then();
    }

    public Mono<List<CachedChannelEntry>> listNewReleases(String userId) {
        return listNewReleases(userId, null);
    }

    public Mono<List<CachedChannelEntry>> listNewReleases(String userId, String accessToken) {
        Flux<CachedChannelEntry> flux = cachedChannelRepository
                .findByUserIdAndLatestVideoIdNotNullAndLatestVideoPublishedAtNotNullOrderByLatestVideoPublishedAtDesc(userId)
                .flatMap(doc -> backfillLatestVideoDetailsIfNeeded(doc, accessToken))
                .map(ChannelCacheService::toEntry)
                .take(20);
        return flux.collectList();
    }

    private Mono<CachedChannel> backfillLatestVideoDetailsIfNeeded(CachedChannel doc, String accessToken) {
        if (doc == null) {
            return Mono.empty();
        }
        if (accessToken == null || accessToken.isBlank()) {
            return Mono.just(doc);
        }
        if (doc.getLatestVideoId() == null || doc.getLatestVideoId().isBlank()) {
            return Mono.just(doc);
        }
        boolean missingDuration = doc.getLatestVideoDuration() == null || doc.getLatestVideoDuration().isBlank();
        boolean missingViewCount = doc.getLatestVideoViewCount() == null;
        if (!missingDuration && !missingViewCount) {
            return Mono.just(doc);
        }

        return youTubeDataApiService
                .getVideoDetails(accessToken, doc.getLatestVideoId())
                .flatMap(details -> {
                    boolean changed = false;
                    if ((doc.getLatestVideoDuration() == null || doc.getLatestVideoDuration().isBlank())
                            && details.duration() != null
                            && !details.duration().isBlank()) {
                        doc.setLatestVideoDuration(details.duration());
                        changed = true;
                    }
                    if (doc.getLatestVideoViewCount() == null && details.viewCount() != null) {
                        doc.setLatestVideoViewCount(details.viewCount());
                        changed = true;
                    }
                    if (!changed) {
                        return Mono.just(doc);
                    }
                    doc.setUpdatedAt(java.time.Instant.now());
                    return cachedChannelRepository.save(doc);
                })
                .switchIfEmpty(Mono.just(doc))
                // 読み取り時の補完なので、失敗してもレスポンス自体は返す
                .onErrorResume(e -> Mono.just(doc));
    }

    private static CachedChannelEntry toEntry(CachedChannel doc) {
        return new CachedChannelEntry(
                doc.getId(),
                doc.getChannelId(),
                doc.getChannelTitle(),
                doc.getChannelDescription(),
                doc.getThumbnailUrl(),
                doc.getIsArtist() != null ? doc.getIsArtist() : false,
                doc.getLatestVideoId(),
                doc.getLatestVideoTitle(),
                doc.getLatestVideoThumbnail(),
                doc.getLatestVideoPublishedAt() != null ? doc.getLatestVideoPublishedAt().toString() : null,
                doc.getLatestVideoDuration(),
                doc.getLatestVideoViewCount(),
                doc.getVideoCount() != null ? doc.getVideoCount() : 0);
    }

    private static java.time.Instant parseInstant(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return java.time.Instant.parse(value);
        } catch (Exception e) {
            return null;
        }
    }

    public record CachedChannelEntry(
            String subscriptionId,
            String channelId,
            String channelTitle,
            String channelDescription,
            String thumbnailUrl,
            boolean isArtist,
            String latestVideoId,
            String latestVideoTitle,
            String latestVideoThumbnail,
            String latestVideoPublishedAt,
            String latestVideoDuration,
            Long latestVideoViewCount,
            int videoCount) {

        public CachedChannelEntry withIsArtist(boolean value) {
            return new CachedChannelEntry(subscriptionId, channelId, channelTitle, channelDescription, thumbnailUrl, value,
                    latestVideoId, latestVideoTitle, latestVideoThumbnail, latestVideoPublishedAt, latestVideoDuration,
                    latestVideoViewCount, videoCount);
        }
    }
}
