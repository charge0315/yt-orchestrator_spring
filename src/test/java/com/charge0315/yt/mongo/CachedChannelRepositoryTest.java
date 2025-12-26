package com.charge0315.yt.mongo;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import reactor.test.StepVerifier;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CachedChannelRepository のテスト。
 * 
 * <p>MongoDB操作のテストです。</p>
 */
@SpringBootTest
class CachedChannelRepositoryTest {

    @Autowired
    private CachedChannelRepository repository;

    private static final String TEST_USER_ID = "test-user-repo";
    private static final String TEST_CHANNEL_ID_1 = "UC_channel_1";
    private static final String TEST_CHANNEL_ID_2 = "UC_channel_2";

    @BeforeEach
    void setUp() {
        // テストデータをクリーンアップ
        repository.deleteAll().block();
    }

    @AfterEach
    void tearDown() {
        repository.deleteAll().block();
    }

    @Test
    @DisplayName("チャンネルを保存して取得できること")
    void shouldSaveAndFindChannel() {
        // テストデータ作成
        CachedChannel channel = createTestChannel(TEST_USER_ID, TEST_CHANNEL_ID_1, "Test Channel 1");

        // 保存
        StepVerifier.create(repository.save(channel))
                .assertNext(saved -> {
                    assertThat(saved.getId()).isNotNull();
                    assertThat(saved.getChannelId()).isEqualTo(TEST_CHANNEL_ID_1);
                    assertThat(saved.getChannelTitle()).isEqualTo("Test Channel 1");
                    assertThat(saved.getUserId()).isEqualTo(TEST_USER_ID);
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("userIdでチャンネルを検索できること")
    void shouldFindByUserId() {
        // テストデータを複数作成
        CachedChannel channel1 = createTestChannel(TEST_USER_ID, TEST_CHANNEL_ID_1, "Channel 1");
        CachedChannel channel2 = createTestChannel(TEST_USER_ID, TEST_CHANNEL_ID_2, "Channel 2");
        CachedChannel channel3 = createTestChannel("other-user", "UC_other", "Other Channel");

        repository.save(channel1).block();
        repository.save(channel2).block();
        repository.save(channel3).block();

        // userIdで検索
        StepVerifier.create(repository.findByUserId(TEST_USER_ID))
                .expectNextCount(2) // TEST_USER_IDのチャンネルが2件
                .verifyComplete();
    }

    @Test
    @DisplayName("userIdとchannelIdでチャンネルを検索できること")
    void shouldFindByUserIdAndChannelId() {
        // テストデータ作成
        CachedChannel channel = createTestChannel(TEST_USER_ID, TEST_CHANNEL_ID_1, "Test Channel");
        repository.save(channel).block();

        // 検索
        StepVerifier.create(repository.findFirstByUserIdAndChannelId(TEST_USER_ID, TEST_CHANNEL_ID_1))
                .assertNext(found -> {
                    assertThat(found.getChannelId()).isEqualTo(TEST_CHANNEL_ID_1);
                    assertThat(found.getChannelTitle()).isEqualTo("Test Channel");
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("userIdとisArtist=trueでチャンネルを検索できること")
    void shouldFindByUserIdAndIsArtist() {
        // アーティストフラグありのチャンネル
        CachedChannel artist = createTestChannel(TEST_USER_ID, TEST_CHANNEL_ID_1, "Artist Channel");
        artist.setIsArtist(true);

        // 通常のチャンネル
        CachedChannel normalChannel = createTestChannel(TEST_USER_ID, TEST_CHANNEL_ID_2, "Normal Channel");
        normalChannel.setIsArtist(false);

        repository.save(artist).block();
        repository.save(normalChannel).block();

        // アーティストのみ検索
        StepVerifier.create(repository.findByUserIdAndIsArtistTrueOrderByChannelTitleAsc(TEST_USER_ID))
                .assertNext(found -> {
                    assertThat(found.getChannelId()).isEqualTo(TEST_CHANNEL_ID_1);
                    assertThat(found.getIsArtist()).isTrue();
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("チャンネルを削除できること")
    void shouldDeleteChannel() {
        // テストデータ作成と保存
        CachedChannel channel = createTestChannel(TEST_USER_ID, TEST_CHANNEL_ID_1, "Test Channel");
        CachedChannel saved = repository.save(channel).block();

        assertThat(saved).isNotNull();
        String id = saved.getId();

        // 削除
        StepVerifier.create(repository.deleteById(id))
                .verifyComplete();

        // 削除確認
        StepVerifier.create(repository.findById(id))
                .verifyComplete(); // 見つからない
    }

    @Test
    @DisplayName("チャンネル情報を更新できること")
    void shouldUpdateChannel() {
        // 保存
        CachedChannel channel = createTestChannel(TEST_USER_ID, TEST_CHANNEL_ID_1, "Original Title");
        CachedChannel saved = repository.save(channel).block();
        assertThat(saved).isNotNull();

        // 更新
        saved.setChannelTitle("Updated Title");
        saved.setLatestVideoId("new-video-123");
        saved.setUpdatedAt(Instant.now());

        CachedChannel updated = repository.save(saved).block();

        // 確認
        assertThat(updated).isNotNull();
        assertThat(updated.getChannelTitle()).isEqualTo("Updated Title");
        assertThat(updated.getLatestVideoId()).isEqualTo("new-video-123");
    }

    @Test
    @DisplayName("存在しないIDでの検索は空の結果を返すこと")
    void shouldReturnEmptyForNonExistentId() {
        StepVerifier.create(repository.findById("non-existent-id"))
                .verifyComplete();
    }

    @Test
    @DisplayName("存在しないuserIdでの検索は空の結果を返すこと")
    void shouldReturnEmptyForNonExistentUserId() {
        StepVerifier.create(repository.findByUserId("non-existent-user"))
                .verifyComplete();
    }

    /**
     * テスト用のCachedChannelオブジェクトを作成します。
     */
    private CachedChannel createTestChannel(String userId, String channelId, String channelTitle) {
        CachedChannel channel = new CachedChannel();
        channel.setUserId(userId);
        channel.setChannelId(channelId);
        channel.setChannelTitle(channelTitle);
        channel.setChannelDescription("Test Description");
        channel.setThumbnailUrl("https://example.com/thumbnail.jpg");
        channel.setIsArtist(false);
        channel.setLatestVideoId("test-video");
        channel.setLatestVideoTitle("Test Video Title");
        channel.setLatestVideoThumbnail("https://example.com/video-thumb.jpg");
        channel.setLatestVideoPublishedAt(Instant.now());
        channel.setCreatedAt(Instant.now());
        channel.setUpdatedAt(Instant.now());
        return channel;
    }
}
