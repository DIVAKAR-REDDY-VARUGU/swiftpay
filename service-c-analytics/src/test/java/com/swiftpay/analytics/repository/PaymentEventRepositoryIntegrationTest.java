package com.swiftpay.analytics.repository;

import com.swiftpay.analytics.dto.SummaryDto;
import com.swiftpay.analytics.dto.TopSenderDto;
import com.swiftpay.analytics.dto.UserStatsDto;
import com.swiftpay.analytics.event.PaymentOutcomeEvent;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

// hits a REAL clickhouse (testcontainers) so the insert + the aggregation SQL are actually exercised.
// skips if docker isnt reachable (like my machine); runs for real on the ci server.
@Testcontainers(disabledWithoutDocker = true)
class PaymentEventRepositoryIntegrationTest {

    @Container
    static GenericContainer<?> clickhouse =
            new GenericContainer<>(DockerImageName.parse("clickhouse/clickhouse-server:24.8"))
                    .withExposedPorts(8123)
                    .waitingFor(Wait.forHttp("/ping").forPort(8123));

    static JdbcTemplate jdbc;
    static PaymentEventRepository repo;

    @BeforeAll
    static void setup() {
        String url = "jdbc:clickhouse://" + clickhouse.getHost() + ":" + clickhouse.getMappedPort(8123) + "/default";
        DataSource ds = DataSourceBuilder.create()
                .driverClassName("com.clickhouse.jdbc.ClickHouseDriver")
                .url(url)
                .username("default")
                .password("")
                .build();
        jdbc = new JdbcTemplate(ds);
        jdbc.execute("CREATE TABLE payment_events (" +
                "transaction_id String, sender_id UInt64, receiver_id UInt64, amount Decimal(18,2), " +
                "status LowCardinality(String), reason String DEFAULT '', event_time DateTime DEFAULT now()) " +
                "ENGINE = ReplacingMergeTree(event_time) ORDER BY (transaction_id)");
        repo = new PaymentEventRepository(jdbc);
    }

    @BeforeEach
    void clean() {
        jdbc.execute("TRUNCATE TABLE payment_events");   // start each test with an empty table
    }

    @Test
    void insertsEventsAndAggregatesThem() {
        repo.insert(new PaymentOutcomeEvent("T1", 1L, 2L, new BigDecimal("100.00"), "COMPLETED", null));
        repo.insert(new PaymentOutcomeEvent("T2", 1L, 3L, new BigDecimal("50.00"), "COMPLETED", null));
        repo.insert(new PaymentOutcomeEvent("T3", 3L, 1L, new BigDecimal("25.00"), "FAILED", "insufficient funds"));

        SummaryDto s = repo.summary();
        assertThat(s.completedCount()).isEqualTo(2);
        assertThat(s.failedCount()).isEqualTo(1);
        assertThat(s.totalVolume()).isEqualByComparingTo("150.00");   // 100 + 50 completed

        List<TopSenderDto> top = repo.topSenders(5);
        assertThat(top).isNotEmpty();
        assertThat(top.get(0).senderId()).isEqualTo(1L);              // sender 1 sent the most
        assertThat(top.get(0).totalSent()).isEqualByComparingTo("150.00");
    }

    @Test
    void userStatsSplitsSentAndReceived() {
        repo.insert(new PaymentOutcomeEvent("U1", 7L, 8L, new BigDecimal("70.00"), "COMPLETED", null));
        repo.insert(new PaymentOutcomeEvent("U2", 9L, 7L, new BigDecimal("30.00"), "COMPLETED", null));

        UserStatsDto stats = repo.userStats(7L);
        assertThat(stats.sentCount()).isEqualTo(1);
        assertThat(stats.sentVolume()).isEqualByComparingTo("70.00");
        assertThat(stats.receivedCount()).isEqualTo(1);
        assertThat(stats.receivedVolume()).isEqualByComparingTo("30.00");
    }
}
