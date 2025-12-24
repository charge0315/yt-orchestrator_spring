package com.charge0315.yt.util;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.WebSession;

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
