package com.charge0315.yt.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import com.charge0315.yt.mongo.CachedChannel;
import com.charge0315.yt.mongo.CachedChannelRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import reactor.core.publisher.Mono;

/**
 * おすすめ（レコメンド）生成サービス。
 *
 * <p>登録済みチャンネルの傾向から、未登録の可能性が高い検索キーワード/チャンネル候補を提案します。</p>
 * <ul>
 *   <li>{@code OPENAI_API_KEY} が設定されている場合は OpenAI を用いた提案を試みます</li>
 *   <li>失敗時や未設定時は、クォータ消費ゼロのフォールバック（ルールベース）で生成します</li>
 * </ul>
 */
@Service
public class RecommendationsService {

    private final CachedChannelRepository cachedChannelRepository;
    private final WebClient openAiClient;
    private final String openAiApiKey;
    private final String openAiModel;
    private final ObjectMapper objectMapper;

    public RecommendationsService(CachedChannelRepository cachedChannelRepository, WebClient.Builder webClientBuilder) {
        this.cachedChannelRepository = cachedChannelRepository;
        this.openAiClient = webClientBuilder
            .baseUrl("https://api.openai.com/v1")
            .build();
        this.openAiApiKey = System.getenv("OPENAI_API_KEY");
        String model = System.getenv("OPENAI_MODEL");
        this.openAiModel = (model != null && !model.isBlank()) ? model : "gpt-4o-mini";
        this.objectMapper = new ObjectMapper();
    }

    public Mono<List<RecommendationEntry>> getRecommendations(String userId) {
        return cachedChannelRepository
                .findByUserId(userId)
                .collectList()
                .flatMap(channels -> maybeBuildOpenAiRecommendations(channels)
                    .onErrorResume(e -> Mono.empty())
                    .filter(list -> list != null && !list.isEmpty())
                    .defaultIfEmpty(buildRecommendations(channels)));
    }

    private Mono<List<RecommendationEntry>> maybeBuildOpenAiRecommendations(List<CachedChannel> cachedChannels) {
        if (cachedChannels == null || cachedChannels.size() < 3) {
            return Mono.empty();
        }
        if (openAiApiKey == null || openAiApiKey.isBlank() || "DUMMY_OPENAI_API_KEY".equals(openAiApiKey)) {
            return Mono.empty();
        }

        Set<String> subscribedNames = new HashSet<>();
        for (CachedChannel ch : cachedChannels) {
            String title = safe(ch.getChannelTitle()).trim();
            if (!title.isEmpty()) {
                subscribedNames.add(title.toLowerCase(Locale.ROOT));
            }
        }

        List<Map<String, Object>> channelHints = cachedChannels.stream()
            .limit(40)
            .map(ch -> Map.<String, Object>of(
                "title", safe(ch.getChannelTitle()),
                "isArtist", Boolean.TRUE.equals(ch.getIsArtist())))
            .toList();

        String userPrompt = String.join("\n",
            "あなたはYouTubeのおすすめ生成アシスタントです。",
            "次の登録チャンネル（抜粋）を参考に、未登録の可能性が高い『検索キーワード/チャンネル名』を5件提案してください。",
            "出力は必ずJSON配列のみ。各要素は {\"title\": string, \"channelTitle\": string, \"reason\": string }。",
            "channelTitle は YouTube 検索に使う文字列にしてください（固有名 or キーワード）。",
            "すでに登録済みっぽい名前は避けてください。",
            "",
            safeJson(channelHints));

        Map<String, Object> req = Map.of(
            "model", openAiModel,
            "messages", List.of(
                Map.of("role", "system", "content", "必ず有効なJSON配列のみを返し、余計な文章は出力しない。"),
                Map.of("role", "user", "content", userPrompt)),
            "temperature", 0.7);

        return openAiClient
            .post()
            .uri("/chat/completions")
            .headers(h -> h.setBearerAuth(openAiApiKey))
            .bodyValue(req)
            .retrieve()
            .bodyToMono(JsonNode.class)
            .map(json -> json.path("choices").path(0).path("message").path("content").asText(""))
            .map(this::extractJsonArray)
            .flatMap(content -> {
                if (content == null || content.isBlank()) {
                    return Mono.empty();
                }

                try {
                    JsonNode parsed = objectMapper.readTree(content);
                    if (parsed == null || !parsed.isArray()) {
                        return Mono.empty();
                    }

                    List<RecommendationEntry> cleaned = new ArrayList<>();
                    Set<String> picked = new HashSet<>();

                    for (JsonNode node : parsed) {
                        if (node == null || !node.isObject()) continue;
                        String title = node.path("title").asText("").trim();
                        String channelTitle = node.path("channelTitle").asText("").trim();
                        String reason = node.path("reason").asText("").trim();
                        if (title.isEmpty() || channelTitle.isEmpty()) continue;
                        String key = channelTitle.toLowerCase(Locale.ROOT);
                        if (picked.contains(key)) continue;
                        if (subscribedNames.contains(key)) continue;
                        picked.add(key);
                        cleaned.add(new RecommendationEntry(title, channelTitle, reason));
                        if (cleaned.size() >= 5) break;
                    }

                    return cleaned.isEmpty() ? Mono.empty() : Mono.just(cleaned);
                } catch (Exception e) {
                    return Mono.empty();
                }
            });
    }

    private String safeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            return "[]";
        }
    }

    private String extractJsonArray(String content) {
        if (content == null) return null;
        int start = content.indexOf('[');
        int end = content.lastIndexOf(']');
        if (start < 0 || end < 0 || end <= start) return null;
        return content.substring(start, end + 1).trim();
    }

    private List<RecommendationEntry> buildRecommendations(List<CachedChannel> cachedChannels) {
        if (cachedChannels == null || cachedChannels.isEmpty()) {
            return List.of();
        }

        Set<String> subscribedNames = new HashSet<>();
        for (CachedChannel ch : cachedChannels) {
            String title = safe(ch.getChannelTitle()).trim();
            if (!title.isEmpty()) {
                subscribedNames.add(title.toLowerCase(Locale.ROOT));
            }
        }

        Map<String, Integer> scores = new HashMap<>();
        scores.put("music", 0);
        scores.put("tech", 0);
        scores.put("gaming", 0);
        scores.put("cooking", 0);
        scores.put("science", 0);
        scores.put("programming", 0);
        scores.put("fitness", 0);
        scores.put("vlogs", 0);

        Pattern music = Pattern.compile("\\b(topic|vevo|music|album|mv|live)\\b|音楽|公式|歌|作業用", Pattern.CASE_INSENSITIVE);
        Pattern tech = Pattern.compile("\\b(tech|gadget|review|iphone|android|pc|laptop)\\b|ガジェット|テック|レビュー",
                Pattern.CASE_INSENSITIVE);
        Pattern gaming = Pattern.compile("\\b(game|gaming|switch|ps\\d|xbox|実況)\\b|ゲーム|実況", Pattern.CASE_INSENSITIVE);
        Pattern cooking = Pattern.compile("\\b(recipe|cooking|kitchen)\\b|料理|レシピ", Pattern.CASE_INSENSITIVE);
        Pattern science = Pattern.compile("\\b(science|math|physics|chemistry|education)\\b|科学|教育|解説", Pattern.CASE_INSENSITIVE);
        Pattern programming = Pattern.compile(
                "\\b(programming|developer|javascript|typescript|python|coding)\\b|プログラミング|開発|エンジニア",
                Pattern.CASE_INSENSITIVE);
        Pattern fitness = Pattern.compile("\\b(workout|fitness|gym|yoga)\\b|筋トレ|フィットネス|ヨガ", Pattern.CASE_INSENSITIVE);
        Pattern vlogs = Pattern.compile("\\b(vlog|daily|life)\\b|日常|ルーティン|vlog", Pattern.CASE_INSENSITIVE);

        for (CachedChannel ch : cachedChannels) {
            String text = (safe(ch.getChannelTitle()) + " " + safe(ch.getChannelDescription())).toLowerCase(Locale.ROOT);
            if (Boolean.TRUE.equals(ch.getIsArtist())) {
                scores.compute("music", (k, v) -> v == null ? 2 : v + 2);
            }
            if (music.matcher(text).find()) scores.compute("music", (k, v) -> v + 1);
            if (tech.matcher(text).find()) scores.compute("tech", (k, v) -> v + 1);
            if (gaming.matcher(text).find()) scores.compute("gaming", (k, v) -> v + 1);
            if (cooking.matcher(text).find()) scores.compute("cooking", (k, v) -> v + 1);
            if (science.matcher(text).find()) scores.compute("science", (k, v) -> v + 1);
            if (programming.matcher(text).find()) scores.compute("programming", (k, v) -> v + 1);
            if (fitness.matcher(text).find()) scores.compute("fitness", (k, v) -> v + 1);
            if (vlogs.matcher(text).find()) scores.compute("vlogs", (k, v) -> v + 1);
        }

        List<String> ranked = scores.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue(Comparator.reverseOrder()))
                .filter(e -> e.getValue() != null && e.getValue() > 0)
                .map(Map.Entry::getKey)
                .toList();

        Map<String, List<RecommendationEntry>> categoryToQueries = buildCategoryQueries();

        List<RecommendationEntry> out = new ArrayList<>();
        Set<String> picked = new HashSet<>();

        List<String> ordered = !ranked.isEmpty() ? ranked : List.of("music", "tech", "science", "gaming");
        for (String cat : ordered) {
            for (RecommendationEntry e : categoryToQueries.getOrDefault(cat, List.of())) {
                if (out.size() >= 5) break;
                String key = safe(e.channelTitle()).toLowerCase(Locale.ROOT);
                if (picked.contains(key)) continue;
                if (subscribedNames.contains(key)) continue;
                picked.add(key);
                out.add(e);
            }
            if (out.size() >= 5) break;
        }

        return out;
    }

    private static Map<String, List<RecommendationEntry>> buildCategoryQueries() {
        Map<String, List<RecommendationEntry>> m = new HashMap<>();
        m.put("music", List.of(
                new RecommendationEntry("作業用BGMを新規開拓", "lofi hip hop live", "音楽系の登録チャンネルが多い"),
                new RecommendationEntry("新譜/リリース情報", "新曲 プレイリスト 2025", "最新の音楽トレンドを追いやすい"),
                new RecommendationEntry("ライブ/セッション", "live session music", "ライブ系のおすすめを探索")));
        m.put("tech", List.of(
                new RecommendationEntry("最新ガジェットの比較", "2025 smartphone comparison", "テック系チャンネルの傾向"),
                new RecommendationEntry("開封・レビュー", "laptop review 2025", "レビュー系が好きそう")));
        m.put("gaming", List.of(
                new RecommendationEntry("実況の新規チャンネル", "ゲーム 実況 おすすめ", "ゲーム系の登録傾向"),
                new RecommendationEntry("最新ゲームトレンド", "new game releases 2025", "新作情報を拾う")));
        m.put("cooking", List.of(
                new RecommendationEntry("時短レシピ", "時短 レシピ 簡単", "料理系の登録傾向"),
                new RecommendationEntry("作り置き", "作り置き 1週間", "日常に役立つ料理コンテンツ")));
        m.put("science", List.of(
                new RecommendationEntry("科学解説の深掘り", "science explained", "解説・教育系が好きそう"),
                new RecommendationEntry("身近な数学", "math explained", "理解が進む系の動画を探索")));
        m.put("programming", List.of(
                new RecommendationEntry("実務寄りのTypeScript", "TypeScript best practices", "開発系チャンネルの傾向"),
                new RecommendationEntry("最新フロントエンド", "React patterns 2025", "技術トレンドを拾う")));
        m.put("fitness", List.of(
                new RecommendationEntry("自宅ワークアウト", "home workout 20 minutes", "フィットネス系の傾向"),
                new RecommendationEntry("ストレッチ/回復", "stretch routine", "継続しやすい内容を探索")));
        m.put("vlogs", List.of(
                new RecommendationEntry("朝ルーティン", "morning routine vlog", "日常系の傾向"),
                new RecommendationEntry("作業/勉強Vlog", "study with me", "落ち着く系コンテンツを探索")));
        return m;
    }

    private static String safe(String s) {
        return s == null ? "" : s;
    }

    public record RecommendationEntry(String title, String channelTitle, String reason) {
    }
}
