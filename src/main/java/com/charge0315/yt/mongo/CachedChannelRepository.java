package com.charge0315.yt.mongo;

import java.time.Instant;

import org.springframework.data.mongodb.repository.ReactiveMongoRepository;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * {@link CachedChannel} を操作するReactive MongoDBリポジトリ。
 *
 * <p>ユーザーIDや artist フラグでの検索、購読解除（削除）などを提供します。</p>
 */
public interface CachedChannelRepository extends ReactiveMongoRepository<CachedChannel, String> {

    Mono<CachedChannel> findFirstByUserIdAndChannelId(String userId, String channelId);

        Flux<CachedChannel> findByUserId(String userId);

    Flux<CachedChannel> findByUserIdAndIsArtistFalseOrderByChannelTitleAsc(String userId);

    Flux<CachedChannel> findByUserIdAndIsArtistTrueOrderByChannelTitleAsc(String userId);

    Flux<CachedChannel> findByUserIdAndLatestVideoIdNotNullAndLatestVideoPublishedAtNotNullOrderByLatestVideoPublishedAtDesc(
            String userId);

    Mono<Long> deleteByUserIdAndChannelId(String userId, String channelId);

    Mono<Long> deleteByUserIdAndId(String userId, String id);

    Flux<CachedChannel> findByUserIdAndLatestVideoPublishedAtAfterOrderByLatestVideoPublishedAtDesc(String userId,
            Instant after);
}
