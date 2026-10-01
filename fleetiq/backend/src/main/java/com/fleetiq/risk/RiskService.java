package com.fleetiq.risk;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fleetiq.common.RedisJsonCache;
import com.fleetiq.ml.MlClient;
import com.fleetiq.ml.MlDtos.RiskFeatures;
import com.fleetiq.ml.MlDtos.RiskPrediction;
import java.time.Duration;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * Customer risk score 0-100 from rental history. Features come from one SQL aggregation;
 * the ML service scores them (rule-based fallback if it's down). Cached 10 minutes.
 */
@Service
public class RiskService {

    public static final int HIGH_RISK = 70;
    private static final String CACHE_KEY = "risk:customers";

    private static final String FEATURES_SQL = """
            SELECT u.id,
                   u.name,
                   COUNT(b.id)                                           AS total_bookings,
                   COUNT(b.id) FILTER (WHERE b.returned_late)            AS late_returns,
                   COUNT(b.id) FILTER (WHERE b.damage_reported)          AS damage_claims,
                   (SELECT COUNT(*) FROM payments p
                      JOIN bookings pb ON pb.id = p.booking_id
                     WHERE pb.customer_id = u.id AND p.status = 'FAILED') AS payment_failures,
                   COALESCE(AVG(EXTRACT(EPOCH FROM (b.actual_return_time - b.end_time)) / 3600.0)
                            FILTER (WHERE b.returned_late), 0)           AS avg_overdue_hours
              FROM users u
              LEFT JOIN bookings b
                ON b.customer_id = u.id AND b.status IN ('CONFIRMED', 'ACTIVE', 'COMPLETED')
             WHERE u.role = 'CUSTOMER'
             GROUP BY u.id, u.name
            """;

    private final JdbcTemplate jdbc;
    private final MlClient ml;
    private final RedisJsonCache cache;

    public RiskService(JdbcTemplate jdbc, MlClient ml, RedisJsonCache cache) {
        this.jdbc = jdbc;
        this.ml = ml;
        this.cache = cache;
    }

    public record CustomerRiskResponse(Long customerId, String name, int totalBookings, int lateReturns,
                                       int damageClaims, int paymentFailures, double avgOverdueHours, int score) {}

    public List<CustomerRiskResponse> customers() {
        return cache.get(CACHE_KEY, new TypeReference<List<CustomerRiskResponse>>() {}, Duration.ofMinutes(10),
                this::compute);
    }

    public int scoreFor(Long customerId) {
        return customers().stream().filter(c -> c.customerId().equals(customerId))
                .mapToInt(CustomerRiskResponse::score).findFirst().orElse(0);
    }

    public boolean isHighRisk(Long customerId) {
        return scoreFor(customerId) >= HIGH_RISK;
    }

    public void invalidate() {
        cache.evict(CACHE_KEY);
    }

    /** Fallback formula: late-return rate dominates, then damage, failed payments and how late. */
    static int heuristicScore(RiskFeatures f) {
        if (f.totalBookings() == 0) {
            return f.paymentFailures() > 0 ? 30 : 10;
        }
        double lateRate = (double) f.lateReturns() / f.totalBookings();
        double score = lateRate * 90 + f.damageClaims() * 12 + f.paymentFailures() * 8
                + Math.min(f.avgOverdueHours(), 12) * 1.5;
        return (int) Math.round(Math.min(100, score));
    }

    private List<CustomerRiskResponse> compute() {
        record Row(Long id, String name, RiskFeatures f) {}
        List<Row> rows = jdbc.query(FEATURES_SQL, (rs, i) -> {
            long id = rs.getLong("id");
            return new Row(id, rs.getString("name"), new RiskFeatures(id,
                    rs.getInt("total_bookings"), rs.getInt("late_returns"), rs.getInt("damage_claims"),
                    rs.getInt("payment_failures"), Math.round(rs.getDouble("avg_overdue_hours") * 10) / 10.0));
        });

        Map<Long, Integer> scores = new HashMap<>();
        ml.predictRisk(rows.stream().map(Row::f).toList()).ifPresent(r -> {
            for (RiskPrediction p : r.predictions()) {
                scores.put(p.customerId(), p.score());
            }
        });

        return rows.stream()
                .map(r -> new CustomerRiskResponse(r.id(), r.name(), r.f().totalBookings(), r.f().lateReturns(),
                        r.f().damageClaims(), r.f().paymentFailures(), r.f().avgOverdueHours(),
                        scores.getOrDefault(r.id(), heuristicScore(r.f()))))
                .sorted(Comparator.comparingInt(CustomerRiskResponse::score).reversed())
                .toList();
    }
}
