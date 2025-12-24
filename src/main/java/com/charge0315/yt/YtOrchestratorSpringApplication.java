package com.charge0315.yt;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * アプリケーションのエントリポイント。
 *
 * <p>Spring Boot (WebFlux) で API サーバを起動します。</p>
 */
@SpringBootApplication
public class YtOrchestratorSpringApplication {

    public static void main(String[] args) {
        SpringApplication.run(YtOrchestratorSpringApplication.class, args);
    }
}
