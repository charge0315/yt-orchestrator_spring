package com.charge0315.yt.mongo;

import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document("cached_channels")
public class CachedChannel {

    @Id
    private String id; // subscriptionId 相当

    private String userId;
    private String channelId;
    private String channelTitle;
    private String channelDescription;
    private String thumbnailUrl;

    private String subscriberCount;

    private Boolean isArtist;

    private String latestVideoId;
    private String latestVideoTitle;
    private String latestVideoThumbnail;
    private Instant latestVideoPublishedAt;

    private String latestVideoDuration;
    private Long latestVideoViewCount;

    private Integer videoCount;

    private Instant createdAt;
    private Instant updatedAt;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getChannelId() {
        return channelId;
    }

    public void setChannelId(String channelId) {
        this.channelId = channelId;
    }

    public String getChannelTitle() {
        return channelTitle;
    }

    public void setChannelTitle(String channelTitle) {
        this.channelTitle = channelTitle;
    }

    public String getChannelDescription() {
        return channelDescription;
    }

    public void setChannelDescription(String channelDescription) {
        this.channelDescription = channelDescription;
    }

    public String getThumbnailUrl() {
        return thumbnailUrl;
    }

    public void setThumbnailUrl(String thumbnailUrl) {
        this.thumbnailUrl = thumbnailUrl;
    }

    public String getSubscriberCount() {
        return subscriberCount;
    }

    public void setSubscriberCount(String subscriberCount) {
        this.subscriberCount = subscriberCount;
    }

    public Boolean getIsArtist() {
        return isArtist;
    }

    public void setIsArtist(Boolean isArtist) {
        this.isArtist = isArtist;
    }

    public String getLatestVideoId() {
        return latestVideoId;
    }

    public void setLatestVideoId(String latestVideoId) {
        this.latestVideoId = latestVideoId;
    }

    public String getLatestVideoTitle() {
        return latestVideoTitle;
    }

    public void setLatestVideoTitle(String latestVideoTitle) {
        this.latestVideoTitle = latestVideoTitle;
    }

    public String getLatestVideoThumbnail() {
        return latestVideoThumbnail;
    }

    public void setLatestVideoThumbnail(String latestVideoThumbnail) {
        this.latestVideoThumbnail = latestVideoThumbnail;
    }

    public Instant getLatestVideoPublishedAt() {
        return latestVideoPublishedAt;
    }

    public void setLatestVideoPublishedAt(Instant latestVideoPublishedAt) {
        this.latestVideoPublishedAt = latestVideoPublishedAt;
    }

    public String getLatestVideoDuration() {
        return latestVideoDuration;
    }

    public void setLatestVideoDuration(String latestVideoDuration) {
        this.latestVideoDuration = latestVideoDuration;
    }

    public Long getLatestVideoViewCount() {
        return latestVideoViewCount;
    }

    public void setLatestVideoViewCount(Long latestVideoViewCount) {
        this.latestVideoViewCount = latestVideoViewCount;
    }

    public Integer getVideoCount() {
        return videoCount;
    }

    public void setVideoCount(Integer videoCount) {
        this.videoCount = videoCount;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
