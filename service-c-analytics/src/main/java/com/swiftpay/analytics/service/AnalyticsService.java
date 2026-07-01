package com.swiftpay.analytics.service;

import com.swiftpay.analytics.dto.SummaryDto;
import com.swiftpay.analytics.dto.TopSenderDto;
import com.swiftpay.analytics.dto.UserStatsDto;
import com.swiftpay.analytics.event.PaymentOutcomeEvent;
import com.swiftpay.analytics.repository.PaymentEventRepository;
import org.springframework.stereotype.Service;

import java.util.List;

// Thin service over the ClickHouse repository: write on each event, read for the API.
@Service
public class AnalyticsService {

    private final PaymentEventRepository repo;

    public AnalyticsService(PaymentEventRepository repo) {
        this.repo = repo;
    }

    public void record(PaymentOutcomeEvent event) {
        repo.insert(event);
    }

    public SummaryDto summary() {
        return repo.summary();
    }

    public List<TopSenderDto> topSenders(int limit) {
        return repo.topSenders(limit);
    }

    public UserStatsDto userStats(long userId) {
        return repo.userStats(userId);
    }
}
