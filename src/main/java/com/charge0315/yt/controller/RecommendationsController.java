package com.charge0315.yt.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.WebSession;

import com.charge0315.yt.util.SessionAuth;
import com.charge0315.yt.service.RecommendationsService;

import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/recommendations")
public class RecommendationsController {

    private final RecommendationsService recommendationsService;

    public RecommendationsController(RecommendationsService recommendationsService) {
        this.recommendationsService = recommendationsService;
    }

    @GetMapping
    Mono<List<RecommendationsService.RecommendationEntry>> list(WebSession session) {
        String userId = SessionAuth.requireUserId(session);
        return recommendationsService.getRecommendations(userId);
    }
}
