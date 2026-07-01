package com.swiftpay.analytics.repository;

import com.swiftpay.analytics.dto.SummaryDto;
import com.swiftpay.analytics.dto.TopSenderDto;
import com.swiftpay.analytics.dto.UserStatsDto;
import com.swiftpay.analytics.event.PaymentOutcomeEvent;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;

// All the ClickHouse SQL. Reads use FINAL so the ReplacingMergeTree collapses any duplicate
// (redelivered) rows by transaction_id before we count them. count() is wrapped in toInt64 so the
// UInt64 maps cleanly to a Java long.
@Repository
public class PaymentEventRepository {

    private final JdbcTemplate jdbc;

    public PaymentEventRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void insert(PaymentOutcomeEvent e) {
        jdbc.update(
                "INSERT INTO payment_events (transaction_id, sender_id, receiver_id, amount, status, reason) " +
                        "VALUES (?, ?, ?, ?, ?, ?)",
                e.transactionId(), e.senderId(), e.receiverId(), e.amount(), e.status(),
                e.reason() == null ? "" : e.reason());
    }

    public SummaryDto summary() {
        long completed = count("SELECT toInt64(count()) FROM payment_events FINAL WHERE status = 'COMPLETED'");
        long failed = count("SELECT toInt64(count()) FROM payment_events FINAL WHERE status = 'FAILED'");
        BigDecimal volume = jdbc.queryForObject(
                "SELECT sum(amount) FROM payment_events FINAL WHERE status = 'COMPLETED'", BigDecimal.class);
        return new SummaryDto(completed, failed, zero(volume));
    }

    public List<TopSenderDto> topSenders(int limit) {
        return jdbc.query(
                "SELECT toInt64(sender_id) AS sender_id, sum(amount) AS total, toInt64(count()) AS cnt " +
                        "FROM payment_events FINAL WHERE status = 'COMPLETED' " +
                        "GROUP BY sender_id ORDER BY total DESC LIMIT ?",
                (rs, i) -> new TopSenderDto(rs.getLong("sender_id"), rs.getBigDecimal("total"), rs.getLong("cnt")),
                limit);
    }

    public UserStatsDto userStats(long userId) {
        long sentCount = count("SELECT toInt64(count()) FROM payment_events FINAL WHERE status='COMPLETED' AND sender_id = ?", userId);
        BigDecimal sentVol = jdbc.queryForObject(
                "SELECT sum(amount) FROM payment_events FINAL WHERE status='COMPLETED' AND sender_id = ?", BigDecimal.class, userId);
        long recvCount = count("SELECT toInt64(count()) FROM payment_events FINAL WHERE status='COMPLETED' AND receiver_id = ?", userId);
        BigDecimal recvVol = jdbc.queryForObject(
                "SELECT sum(amount) FROM payment_events FINAL WHERE status='COMPLETED' AND receiver_id = ?", BigDecimal.class, userId);
        return new UserStatsDto(userId, sentCount, zero(sentVol), recvCount, zero(recvVol));
    }

    private long count(String sql, Object... args) {
        Long v = jdbc.queryForObject(sql, Long.class, args);
        return v == null ? 0L : v;
    }

    private static BigDecimal zero(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }
}
