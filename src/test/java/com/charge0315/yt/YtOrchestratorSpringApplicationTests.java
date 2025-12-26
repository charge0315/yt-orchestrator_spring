package com.charge0315.yt;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * アプリケーション全体の統合テスト。
 */
@SpringBootTest
class YtOrchestratorSpringApplicationTests {

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    private ReactiveMongoTemplate reactiveMongoTemplate;

    @Test
    @DisplayName("Spring Bootコンテキストが正常にロードされること")
    void contextLoads() {
        assertThat(applicationContext).isNotNull();
    }

    @Test
    @DisplayName("必須のBeanが登録されていること")
    void shouldHaveRequiredBeans() {
        // コントローラー
        assertThat(applicationContext.containsBean("healthController")).isTrue();
        assertThat(applicationContext.containsBean("artistsController")).isTrue();
        assertThat(applicationContext.containsBean("authController")).isTrue();
        
        // サービス
        assertThat(applicationContext.containsBean("channelCacheService")).isTrue();
        
        // リポジトリ
        assertThat(applicationContext.containsBean("cachedChannelRepository")).isTrue();
    }

    @Test
    @DisplayName("MongoDBへの接続が確立されていること")
    void shouldConnectToMongoDB() {
        assertThat(reactiveMongoTemplate).isNotNull();
        
        // データベース名の確認
        String databaseName = reactiveMongoTemplate.getMongoDatabase()
                .block()
                .getName();
        
        assertThat(databaseName).isNotNull();
        assertThat(databaseName).isNotEmpty();
    }

    @Test
    @DisplayName("WebFlux設定が有効になっていること")
    void shouldHaveWebFluxConfiguration() {
        assertThat(applicationContext.containsBean("webClientConfig")).isTrue();
    }

    @Test
    @DisplayName("CORS設定が有効になっていること")
    void shouldHaveCorsConfiguration() {
        assertThat(applicationContext.containsBean("corsConfig")).isTrue();
    }
}
