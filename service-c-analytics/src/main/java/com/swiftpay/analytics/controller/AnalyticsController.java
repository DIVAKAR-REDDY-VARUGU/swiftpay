package com.swiftpay.analytics.controller;

import com.swiftpay.analytics.dto.SummaryDto;
import com.swiftpay.analytics.dto.TopSenderDto;
import com.swiftpay.analytics.dto.UserStatsDto;
import com.swiftpay.analytics.service.AnalyticsService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/v1/analytics")
public class AnalyticsController {

    private final AnalyticsService analytics;

    public AnalyticsController(AnalyticsService analytics) {
        this.analytics = analytics;
    }

    // GET /v1/analytics/summary — completed/failed counts and total settled volume.
    @GetMapping("/summary")
    public SummaryDto summary() {
        return analytics.summary();
    }

    // GET /v1/analytics/top-senders?limit=5 — biggest senders by settled volume.
    @GetMapping("/top-senders")
    public List<TopSenderDto> topSenders(@RequestParam(defaultValue = "5") int limit) {
        return analytics.topSenders(limit);
    }

    // GET /v1/analytics/users/{id} — one user's sent/received totals.
    @GetMapping("/users/{id}")
    public UserStatsDto user(@PathVariable long id) {
        return analytics.userStats(id);
    }
}
