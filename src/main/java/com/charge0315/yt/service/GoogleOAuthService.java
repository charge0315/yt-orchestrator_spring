package com.charge0315.yt.service;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.util.UriComponentsBuilder;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;

import reactor.core.publisher.Mono;

/**
 * Google OAuth の実装（認可URL生成、トークン交換、ユーザー情報取得）。
 *
 * <p>YouTube Data API v3 操作に必要なスコープを含め、
 * 取得したアクセストークン/リフレッシュトークンをセッションへ保存する前提で利用します。</p>
 */
@Service
public class GoogleOAuthService {

    private final WebClient webClient;
    private final ObjectMapper objectMapper;

    private volatile ClientSecrets cachedClientSecrets;

    public GoogleOAuthService(WebClient.Builder builder, ObjectMapper objectMapper) {
        this.webClient = builder.build();
        this.objectMapper = objectMapper;
    }

    public URI buildAuthorizationUri(ServerWebExchange exchange) {
        String clientId = requireClientId();
        String redirectUri = resolveRedirectUri(exchange);

        String scope = buildScope();

        return UriComponentsBuilder
                .fromUriString("https://accounts.google.com/o/oauth2/v2/auth")
                .queryParam("client_id", clientId)
                .queryParam("redirect_uri", redirectUri)
                .queryParam("response_type", "code")
                .queryParam("scope", scope)
                .queryParam("access_type", "offline")
                .queryParam("prompt", "consent")
                .build(true)
                .toUri();
    }

    private static String buildScope() {
        String fromEnv = System.getenv("GOOGLE_SCOPES");
        if (fromEnv != null && !fromEnv.isBlank()) {
            List<String> scopes = new ArrayList<>();
            Arrays.stream(fromEnv.split("[\\s,]+"))
                    .map(String::trim)
                    .filter(s -> !s.isBlank())
                    .forEach(scopes::add);
            if (!scopes.isEmpty()) {
                return String.join(" ", scopes);
            }
        }

        // 最低限: YouTube Data API 操作 + ユーザー情報取得
        return String.join(" ",
                "https://www.googleapis.com/auth/youtube",
                "https://www.googleapis.com/auth/userinfo.email",
                "https://www.googleapis.com/auth/userinfo.profile");
    }

    public Mono<TokenResponse> exchangeCodeForTokens(ServerWebExchange exchange, String code) {
        String clientId = requireClientId();
        String clientSecret = requireClientSecret();
        String redirectUri = resolveRedirectUri(exchange);

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("code", code);
        form.add("client_id", clientId);
        form.add("client_secret", clientSecret);
        form.add("redirect_uri", redirectUri);
        form.add("grant_type", "authorization_code");

        return webClient
                .post()
                .uri("https://oauth2.googleapis.com/token")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .bodyValue(form)
                .retrieve()
                .bodyToMono(JsonNode.class)
                .map(json -> {
                    String accessToken = text(json, "access_token");
                    String refreshToken = text(json, "refresh_token");
                    long expiresIn = json.hasNonNull("expires_in") ? json.get("expires_in").asLong() : 0L;
                    Instant expiresAt = expiresIn > 0 ? Instant.now().plus(Duration.ofSeconds(expiresIn)) : null;
                    return new TokenResponse(accessToken, refreshToken, expiresAt);
                });
    }

    public Mono<UserInfo> fetchUserInfo(String accessToken) {
        return webClient
                .get()
                .uri("https://www.googleapis.com/oauth2/v2/userinfo")
                .headers(h -> h.setBearerAuth(accessToken))
                .retrieve()
                .bodyToMono(JsonNode.class)
                .map(json -> new UserInfo(
                        text(json, "id"),
                        text(json, "email"),
                        text(json, "name"),
                        text(json, "picture")));
    }

    private static String text(JsonNode json, String field) {
        return json != null && json.hasNonNull(field) ? json.get(field).asText() : null;
    }

    private static String requireEnv(String name) {
        String val = System.getenv(name);
        if (val == null || val.isBlank()) {
            throw new IllegalStateException("Missing env var: " + name);
        }
        return val;
    }

    private String requireClientId() {
        String env = System.getenv("GOOGLE_CLIENT_ID");
        if (env != null && !env.isBlank()) {
            return env;
        }
        return loadClientSecrets().clientId();
    }

    private String requireClientSecret() {
        String env = System.getenv("GOOGLE_CLIENT_SECRET");
        if (env != null && !env.isBlank()) {
            return env;
        }
        return loadClientSecrets().clientSecret();
    }

    private ClientSecrets loadClientSecrets() {
        ClientSecrets existing = cachedClientSecrets;
        if (existing != null) {
            return existing;
        }

        synchronized (this) {
            if (cachedClientSecrets != null) {
                return cachedClientSecrets;
            }

            ClientSecrets loaded = tryLoadClientSecretsFromFiles();
            cachedClientSecrets = loaded;
            return loaded;
        }
    }

    private ClientSecrets tryLoadClientSecretsFromFiles() {
        String fromEnvPath = System.getenv("GOOGLE_CLIENT_SECRET_FILE");
        List<Path> candidates = new ArrayList<>();
        if (fromEnvPath != null && !fromEnvPath.isBlank()) {
            candidates.add(Paths.get(fromEnvPath));
        }

        // ローカル開発: プロジェクト配置に合わせた代表的な候補
        candidates.add(Paths.get("client_secret.json"));
        candidates.add(Paths.get("packages", "client_secret.json"));
        candidates.add(Paths.get("..", "client_secret.json"));
        candidates.add(Paths.get("..", "packages", "client_secret.json"));

        for (Path candidate : candidates) {
            Path normalized = candidate.normalize();
            if (!Files.exists(normalized)) {
                continue;
            }
            if (!Files.isRegularFile(normalized)) {
                continue;
            }

            try {
                String raw = Files.readString(normalized);
                JsonNode root = objectMapper.readTree(raw);
                JsonNode section = null;
                if (root != null) {
                    if (root.has("installed")) {
                        section = root.get("installed");
                    } else if (root.has("web")) {
                        section = root.get("web");
                    } else {
                        section = root;
                    }
                }

                String clientId = text(section, "client_id");
                String clientSecret = text(section, "client_secret");

                if (clientId != null && !clientId.isBlank() && clientSecret != null && !clientSecret.isBlank()) {
                    return new ClientSecrets(clientId, clientSecret);
                }
            } catch (IOException e) {
                // 次の候補へ
                continue;
            }
        }

        throw new IllegalStateException(
                "Missing Google OAuth config. Set GOOGLE_CLIENT_ID and GOOGLE_CLIENT_SECRET, " +
                        "or provide GOOGLE_CLIENT_SECRET_FILE, or place client_secret.json in the repo root.");
    }

    private static String resolveRedirectUri(ServerWebExchange exchange) {
        String fromEnv = System.getenv("GOOGLE_REDIRECT_URI");
        if (fromEnv != null && !fromEnv.isBlank()) {
            return fromEnv;
        }

        String backendUrl = System.getenv("BACKEND_URL");
        if (backendUrl != null && !backendUrl.isBlank()) {
            String normalized = backendUrl.replaceAll("/+$", "");
            return normalized + "/api/auth/google/callback";
        }

        // 受信リクエストから推定（ローカル開発向け）
        String scheme = exchange.getRequest().getURI().getScheme();
        String forwardedProto = exchange.getRequest().getHeaders().getFirst("X-Forwarded-Proto");
        if (forwardedProto != null && !forwardedProto.isBlank()) {
            scheme = forwardedProto;
        }

        String host = exchange.getRequest().getURI().getHost();
        int port = exchange.getRequest().getURI().getPort();

        return UriComponentsBuilder
                .newInstance()
                .scheme(scheme)
                .host(host)
                .port(port)
                .path("/api/auth/google/callback")
                .build()
                .toUriString();
    }

    public record TokenResponse(String accessToken, String refreshToken, Instant expiresAt) {
    }

    public record UserInfo(String id, String email, String name, String picture) {
        public Map<String, Object> toSessionAttributes() {
            return Map.of(
                    "userId", id,
                    "email", email,
                    "name", name,
                    "picture", picture);
        }
    }

    private record ClientSecrets(String clientId, String clientSecret) {
    }
}
