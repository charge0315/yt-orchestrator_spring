package com.charge0315.yt.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.server.RequestPredicates;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerResponse;

/**
 * SPA (Vite/React) のためのフォールバックルーティング。
 *
 * <p>静的ファイル以外のパスは index.html を返し、
 * React Router などのクライアントサイドルーティングを成立させます。</p>
 */
@Configuration
public class SpaRoutingConfig {

    @Bean
    RouterFunction<ServerResponse> spaIndexFallback() {
        return RouterFunctions.route(
                RequestPredicates.GET("/**")
                        .and(request -> {
                            String path = request.path();
                            if (path.startsWith("/api") || path.startsWith("/actuator")) {
                                return false;
                            }
                            // 拡張子付きの静的アセットは静的リソースハンドラに任せる
                            return !path.contains(".");
                        }),
                request -> ServerResponse.ok()
                        .contentType(MediaType.TEXT_HTML)
                        .body(BodyInserters.fromResource(new ClassPathResource("static/index.html"))));
    }
}
