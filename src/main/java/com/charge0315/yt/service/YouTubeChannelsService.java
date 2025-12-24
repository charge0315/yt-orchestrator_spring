package com.charge0315.yt.service;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import com.charge0315.yt.mongo.CachedChannel;
import com.charge0315.yt.mongo.CachedChannelRepository;

import reactor.core.publisher.Mono;

/**
 * YouTubeチャンネル管理（フロント互換）用サービス。
 *
 * <p>MongoDB の {@link CachedChannel} を基点に、フロントのチャンネル一覧/購読操作が
 * 期待する形へ整形して返します。必要に応じて YouTube Data API を呼び、
 * 最新動画情報などを補完します。</p>
 */
@Service
public class YouTubeChannelsService {

    private final YouTubeDataApiService youTubeDataApiService;

    private final CachedChannelRepository cachedChannelRepository;

    public YouTubeChannelsService(YouTubeDataApiService youTubeDataApiService, CachedChannelRepository cachedChannelRepository) {
        this.youTubeDataApiService = youTubeDataApiService;
        this.cachedChannelRepository = cachedChannelRepository;
    }

    public Mono<List<YouTubeChannelEntry>> getAll(String userId) {
        return getAll(userId, null);
    }

    public Mono<List<YouTubeChannelEntry>> getAll(String userId, String accessToken) {
        // youtubeChannels は通常チャンネル（isArtist=false）を対象にする
        return cachedChannelRepository
                .findByUserIdAndIsArtistFalseOrderByChannelTitleAsc(userId)
                .flatMap(doc -> backfillLatestVideoDetailsIfNeeded(doc, accessToken))
                .map(YouTubeChannelsService::toEntry)
                .collectList();
    }

    public Mono<YouTubeChannelEntry> subscribe(String userId, String accessToken, SubscribeRequest req) {
        if (req == null || !StringUtils.hasText(req.channelId())) {
            return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST, "channelId_required"));
        }

        // 既に登録済みならそれを返す
        Mono<YouTubeChannelEntry> existing = cachedChannelRepository
                .findFirstByUserIdAndChannelId(userId, req.channelId())
                .map(YouTubeChannelsService::toEntry);

        Mono<YouTubeDataApiService.ChannelDetails> detailsMono = youTubeDataApiService.getChannelDetails(accessToken,
                req.channelId());
        Mono<YouTubeDataApiService.LatestVideo> latestMono = youTubeDataApiService
                .fetchLatestVideoForChannel(accessToken, req.channelId());

        Mono<YouTubeChannelEntry> created = Mono
                .zip(detailsMono, latestMono.defaultIfEmpty(new YouTubeDataApiService.LatestVideo(null, null, null, null)))
                .flatMap(tuple -> {
                    YouTubeDataApiService.ChannelDetails details = tuple.getT1();
                    YouTubeDataApiService.LatestVideo latest = tuple.getT2();

                    Mono<YouTubeDataApiService.VideoDetails> videoDetailsMono = StringUtils.hasText(latest.videoId())
                            ? youTubeDataApiService.getVideoDetails(accessToken, latest.videoId())
                                    .switchIfEmpty(Mono.just(new YouTubeDataApiService.VideoDetails(null, null)))
                            : Mono.just(new YouTubeDataApiService.VideoDetails(null, null));

                    return videoDetailsMono.flatMap(videoDetails -> {
                        String id = UUID.randomUUID().toString();
                        Instant now = Instant.now();

                        CachedChannel doc = new CachedChannel();
                        doc.setId(id);
                        doc.setUserId(userId);
                        doc.setChannelId(details.channelId());
                        doc.setChannelTitle(details.title());
                        doc.setChannelDescription(details.description());
                        doc.setThumbnailUrl(details.thumbnailUrl());
                        doc.setSubscriberCount(details.subscriberCount());
                        doc.setIsArtist(false);
                        doc.setVideoCount(0);
                        doc.setCreatedAt(now);
                        doc.setUpdatedAt(now);

                        if (StringUtils.hasText(latest.videoId())) {
                            doc.setLatestVideoId(latest.videoId());
                            doc.setLatestVideoTitle(latest.title());
                            doc.setLatestVideoThumbnail(latest.thumbnailUrl());
                            doc.setLatestVideoPublishedAt(parseInstant(latest.publishedAt()));
                            doc.setLatestVideoDuration(videoDetails.duration());
                            doc.setLatestVideoViewCount(videoDetails.viewCount());
                        }

                        return cachedChannelRepository.save(doc);
                    });
                })
                .map(YouTubeChannelsService::toEntry);

        return existing.switchIfEmpty(created);
    }

    public Mono<Void> unsubscribe(String userId, String id) {
        return cachedChannelRepository.deleteByUserIdAndId(userId, id).then();
    }

    public Mono<List<LatestVideo>> getLatestVideos(String userId) {
        return getLatestVideos(userId, null);
    }

    public Mono<List<LatestVideo>> getLatestVideos(String userId, String accessToken) {
        return getAll(userId, accessToken)
                .map(channels -> channels.stream()
                        .flatMap(ch -> ch.latestVideos().stream())
                        .sorted(Comparator.comparing(YouTubeChannelsService::parseInstantSafe).reversed())
                        .toList());
    }

    private Mono<CachedChannel> backfillLatestVideoDetailsIfNeeded(CachedChannel doc, String accessToken) {
        if (doc == null) {
            return Mono.empty();
        }
        if (!StringUtils.hasText(accessToken)) {
            return Mono.just(doc);
        }
        if (!StringUtils.hasText(doc.getLatestVideoId())) {
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
                            && StringUtils.hasText(details.duration())) {
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
                    doc.setUpdatedAt(Instant.now());
                    return cachedChannelRepository.save(doc);
                })
                .switchIfEmpty(Mono.just(doc))
                // 読み取り時の補完なので、失敗してもレスポンス自体は返す
                .onErrorResume(e -> Mono.just(doc));
    }

    public Mono<YouTubeChannelEntry> updateVideos(String userId, String id, List<LatestVideo> latestVideos) {
        List<LatestVideo> safe = latestVideos != null ? latestVideos : List.of();
        // cached_channels は「最新1件」しか持たないため、渡された配列から一番新しいものを反映する
        return cachedChannelRepository
                .findById(id)
                .filter(doc -> userId.equals(doc.getUserId()))
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND, "not_found")))
                .flatMap(doc -> {
                    doc.setUpdatedAt(Instant.now());
                    LatestVideo newest = safe.stream()
                            .max(Comparator.comparing(YouTubeChannelsService::parseInstantSafe))
                            .orElse(null);
                    if (newest == null || !StringUtils.hasText(newest.videoId())) {
                        doc.setLatestVideoId(null);
                        doc.setLatestVideoTitle(null);
                        doc.setLatestVideoThumbnail(null);
                        doc.setLatestVideoPublishedAt(null);
                        doc.setLatestVideoDuration(null);
                        doc.setLatestVideoViewCount(null);
                    } else {
                        doc.setLatestVideoId(newest.videoId());
                        doc.setLatestVideoTitle(newest.title());
                        doc.setLatestVideoThumbnail(newest.thumbnail());
                        doc.setLatestVideoPublishedAt(parseInstant(newest.publishedAt()));
                        doc.setLatestVideoDuration(newest.duration());
                        doc.setLatestVideoViewCount(newest.viewCount());
                    }
                    return cachedChannelRepository.save(doc);
                })
                .map(YouTubeChannelsService::toEntry);
    }

    private static Instant parseInstantSafe(LatestVideo v) {
        try {
            return Instant.parse(v.publishedAt());
        } catch (Exception e) {
            return Instant.EPOCH;
        }
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

    private static YouTubeChannelEntry toEntry(CachedChannel doc) {
        // cached_channels は latestVideo* を1件だけ持つので、latestVideos は 0 or 1 件にマップ
        List<LatestVideo> latest = (doc.getLatestVideoId() != null && !doc.getLatestVideoId().isBlank())
                ? List.of(new LatestVideo(
                        doc.getLatestVideoId(),
                        doc.getLatestVideoTitle(),
                        doc.getLatestVideoPublishedAt() != null ? doc.getLatestVideoPublishedAt().toString() : null,
                        doc.getLatestVideoThumbnail(),
                doc.getLatestVideoDuration(),
                doc.getLatestVideoViewCount(),
                        doc.getChannelTitle(),
                        doc.getChannelId(),
                        doc.getThumbnailUrl()))
                : List.of();

        return new YouTubeChannelEntry(
                doc.getId(),
                doc.getChannelTitle(),
                doc.getChannelId(),
                doc.getThumbnailUrl(),
                doc.getChannelDescription(),
            doc.getSubscriberCount(),
                latest,
                doc.getUserId(),
                doc.getCreatedAt() != null ? doc.getCreatedAt().toString() : null,
                doc.getUpdatedAt() != null ? doc.getUpdatedAt().toString() : null);
    }

    public record SubscribeRequest(
            String name,
            String channelId,
            String thumbnail,
            String description,
            String subscriberCount) {
    }

    public record LatestVideo(
            String videoId,
            String title,
            String publishedAt,
            String thumbnail,
            String duration,
            Long viewCount,
            String channelName,
            String channelId,
            String channelThumbnail) {
    }

    public record YouTubeChannelEntry(
            String id,
            String name,
            String channelId,
            String thumbnail,
            String description,
            String subscriberCount,
            List<LatestVideo> latestVideos,
            String userId,
            String subscribedAt,
            String lastChecked) {

        public YouTubeChannelEntry withLatestVideos(List<LatestVideo> latestVideos) {
            return new YouTubeChannelEntry(id, name, channelId, thumbnail, description, subscriberCount, latestVideos,
                    userId, subscribedAt, lastChecked);
        }
    }
}
