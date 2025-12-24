package com.charge0315.yt.util;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.WebSession;

/**
 * WebSession（Cookieセッション）から認証情報を取得するヘルパー。
 *
 * <p>このプロジェクトはフロントからの {@code withCredentials: true} を前提に、
 * セッション属性（例: {@code userId}, {@code youtubeAccessToken}）で認証状態を管理します。</p>
 */
public final class SessionAuth {

    private SessionAuth() {
    }

    public static String requireUserId(WebSession session) {
        String userId = session.getAttribute("userId");
        if (userId == null || userId.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }
        return userId;
    }

    public static String requireYouTubeAccessToken(WebSession session) {
        String token = session.getAttribute("youtubeAccessToken");
        if (token == null || token.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "YouTube access token missing");
        }
        return token;
    }

    public static String getYouTubeAccessTokenOrNull(WebSession session) {
        String token = session.getAttribute("youtubeAccessToken");
        if (token == null || token.isBlank()) {
            return null;
        }
        return token;
    }
}
