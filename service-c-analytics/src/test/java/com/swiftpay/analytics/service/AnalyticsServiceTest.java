package com.swiftpay.analytics.service;

import com.swiftpay.analytics.dto.SummaryDto;
import com.swiftpay.analytics.dto.TopSenderDto;
import com.swiftpay.analytics.dto.UserStatsDto;
import com.swiftpay.analytics.event.PaymentOutcomeEvent;
import com.swiftpay.analytics.repository.PaymentEventRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// unit tests - the clickhouse repository is mocked, so this just checks the service passes calls through.
@ExtendWith(MockitoExtension.class)
class AnalyticsServiceTest {

    @Mock PaymentEventRepository repo;
    @InjectMocks AnalyticsService service;

    @Test
    void recordInsertsTheEvent() {
        // every outcome we consume off kafka should turn into one insert into clickhouse
        PaymentOutcomeEvent e = new PaymentOutcomeEvent("T1", 1L, 2L, new BigDecimal("100.00"), "COMPLETED", null);
        service.record(e);
        verify(repo).insert(e);
    }

    @Test
    void summaryComesFromTheRepo() {
        // the service is thin - it should just hand back whatever the repo aggregates
        SummaryDto dto = new SummaryDto(3, 1, new BigDecimal("300.00"));
        when(repo.summary()).thenReturn(dto);
        assertThat(service.summary()).isEqualTo(dto);
    }

    @Test
    void topSendersPassesTheLimitThrough() {
        // whatever limit the controller asks for must reach the repo unchanged
        List<TopSenderDto> top = List.of(new TopSenderDto(1L, new BigDecimal("300.00"), 3));
        when(repo.topSenders(5)).thenReturn(top);
        assertThat(service.topSenders(5)).isEqualTo(top);
        verify(repo).topSenders(5);
    }

    @Test
    void userStatsComesFromTheRepo() {
        UserStatsDto dto = new UserStatsDto(1L, 2, new BigDecimal("200.00"), 1, new BigDecimal("50.00"));
        when(repo.userStats(1L)).thenReturn(dto);
        assertThat(service.userStats(1L)).isEqualTo(dto);
    }
}
