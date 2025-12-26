package com.charge0315.yt.mongo;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CachedChannel エンティティのテスト。
 */
class CachedChannelTest {

    @Test
    @DisplayName("CachedChannelオブジェクトを作成できること")
    void shouldCreateCachedChannel() {
        CachedChannel channel = new CachedChannel();
        
        channel.setId("test-id-123");
        channel.setUserId("user-123");
        channel.setChannelId("UC_channel_123");
        channel.setChannelTitle("Test Channel");
        channel.setChannelDescription("This is a test channel");
        channel.setThumbnailUrl("https://example.com/thumb.jpg");
        channel.setSubscriberCount("1000");
        channel.setIsArtist(true);
        channel.setLatestVideoId("video-123");
        channel.setLatestVideoTitle("Latest Video");
        channel.setLatestVideoThumbnail("https://example.com/video-thumb.jpg");
        channel.setLatestVideoDuration("PT5M30S");
        channel.setLatestVideoViewCount(5000L);
        channel.setVideoCount(50);
        
        Instant now = Instant.now();
        channel.setCreatedAt(now);
        channel.setUpdatedAt(now);
        channel.setLatestVideoPublishedAt(now);

        // 検証
        assertThat(channel.getId()).isEqualTo("test-id-123");
        assertThat(channel.getUserId()).isEqualTo("user-123");
        assertThat(channel.getChannelId()).isEqualTo("UC_channel_123");
        assertThat(channel.getChannelTitle()).isEqualTo("Test Channel");
        assertThat(channel.getChannelDescription()).isEqualTo("This is a test channel");
        assertThat(channel.getThumbnailUrl()).isEqualTo("https://example.com/thumb.jpg");
        assertThat(channel.getSubscriberCount()).isEqualTo("1000");
        assertThat(channel.getIsArtist()).isTrue();
        assertThat(channel.getLatestVideoId()).isEqualTo("video-123");
        assertThat(channel.getLatestVideoTitle()).isEqualTo("Latest Video");
        assertThat(channel.getLatestVideoThumbnail()).isEqualTo("https://example.com/video-thumb.jpg");
        assertThat(channel.getLatestVideoDuration()).isEqualTo("PT5M30S");
        assertThat(channel.getLatestVideoViewCount()).isEqualTo(5000L);
        assertThat(channel.getVideoCount()).isEqualTo(50);
        assertThat(channel.getCreatedAt()).isEqualTo(now);
        assertThat(channel.getUpdatedAt()).isEqualTo(now);
        assertThat(channel.getLatestVideoPublishedAt()).isEqualTo(now);
    }

    @Test
    @DisplayName("デフォルト値でCachedChannelオブジェクトを作成できること")
    void shouldCreateCachedChannelWithDefaults() {
        CachedChannel channel = new CachedChannel();

        // デフォルトはnull
        assertThat(channel.getId()).isNull();
        assertThat(channel.getUserId()).isNull();
        assertThat(channel.getChannelId()).isNull();
        assertThat(channel.getIsArtist()).isNull();
    }

    @Test
    @DisplayName("isArtistフラグを設定・取得できること")
    void shouldSetAndGetIsArtistFlag() {
        CachedChannel channel = new CachedChannel();

        channel.setIsArtist(true);
        assertThat(channel.getIsArtist()).isTrue();

        channel.setIsArtist(false);
        assertThat(channel.getIsArtist()).isFalse();

        channel.setIsArtist(null);
        assertThat(channel.getIsArtist()).isNull();
    }

    @Test
    @DisplayName("タイムスタンプフィールドを設定・取得できること")
    void shouldSetAndGetTimestamps() {
        CachedChannel channel = new CachedChannel();

        Instant created = Instant.now().minusSeconds(3600);
        Instant updated = Instant.now();
        Instant published = Instant.now().minusSeconds(1800);

        channel.setCreatedAt(created);
        channel.setUpdatedAt(updated);
        channel.setLatestVideoPublishedAt(published);

        assertThat(channel.getCreatedAt()).isEqualTo(created);
        assertThat(channel.getUpdatedAt()).isEqualTo(updated);
        assertThat(channel.getLatestVideoPublishedAt()).isEqualTo(published);
    }

    @Test
    @DisplayName("動画情報フィールドを設定・取得できること")
    void shouldSetAndGetVideoInfo() {
        CachedChannel channel = new CachedChannel();

        channel.setLatestVideoId("video-abc-123");
        channel.setLatestVideoTitle("Amazing Video Title");
        channel.setLatestVideoThumbnail("https://img.youtube.com/vi/abc/default.jpg");
        channel.setLatestVideoDuration("PT10M25S");
        channel.setLatestVideoViewCount(12345L);
        channel.setVideoCount(100);

        assertThat(channel.getLatestVideoId()).isEqualTo("video-abc-123");
        assertThat(channel.getLatestVideoTitle()).isEqualTo("Amazing Video Title");
        assertThat(channel.getLatestVideoThumbnail()).isEqualTo("https://img.youtube.com/vi/abc/default.jpg");
        assertThat(channel.getLatestVideoDuration()).isEqualTo("PT10M25S");
        assertThat(channel.getLatestVideoViewCount()).isEqualTo(12345L);
        assertThat(channel.getVideoCount()).isEqualTo(100);
    }
}
