package com.fleetiq.analytics;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fleetiq.common.Money;
import com.fleetiq.common.RedisJsonCache;
import com.fleetiq.config.AppProperties;
import com.fleetiq.fleet.VehicleRepository;
import com.fleetiq.fleet.VehicleStatus;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * Fleet utilisation = booked vehicle-hours / available vehicle-hours.
 * Computed in SQL (generate_series + range overlap) and cached for one minute.
 */
@Service
public class AnalyticsService {

    private static final String TREND_SQL = """
            WITH days AS (
                SELECT gs AS local_day,
                       gs AT TIME ZONE 'Asia/Kolkata' AS day_start
                  FROM generate_series(((now() AT TIME ZONE 'Asia/Kolkata')::date - 13)::timestamp,
                                       (now() AT TIME ZONE 'Asia/Kolkata')::date::timestamp,
                                       interval '1 day') AS gs
            )
            SELECT to_char(d.local_day, 'DD Mon') AS label,
                   COALESCE((
                       SELECT SUM(EXTRACT(EPOCH FROM (
                                  LEAST(COALESCE(b.actual_return_time, b.end_time), d.day_start + interval '1 day', now())
                                - GREATEST(b.start_time, d.day_start))) / 3600.0)
                         FROM bookings b
                        WHERE b.status IN ('CONFIRMED', 'ACTIVE', 'COMPLETED')
                          AND b.start_time < LEAST(d.day_start + interval '1 day', now())
                          AND COALESCE(b.actual_return_time, b.end_time) > d.day_start
                   ), 0) AS booked_hours,
                   COALESCE((
                       SELECT SUM(p.amount)
                         FROM payments p
                        WHERE p.status = 'SUCCESS'
                          AND p.created_at >= d.day_start
                          AND p.created_at <  d.day_start + interval '1 day'
                   ), 0) AS revenue
              FROM days d
             ORDER BY d.local_day
            """;

    private static final String BY_TYPE_SQL = """
            SELECT v.type,
                   COUNT(DISTINCT v.id) AS fleet,
                   COALESCE(SUM(EXTRACT(EPOCH FROM (
                        LEAST(COALESCE(b.actual_return_time, b.end_time), now())
                      - GREATEST(b.start_time, now() - interval '7 days'))) / 3600.0), 0) AS booked_hours
              FROM vehicles v
              LEFT JOIN bookings b
                ON b.vehicle_id = v.id
               AND b.status IN ('CONFIRMED', 'ACTIVE', 'COMPLETED')
               AND b.start_time < now()
               AND COALESCE(b.actual_return_time, b.end_time) > now() - interval '7 days'
             GROUP BY v.type
             ORDER BY v.type
            """;

    private static final String REVENUE_TODAY_SQL = """
            SELECT COALESCE(SUM(amount), 0)
              FROM payments
             WHERE status = 'SUCCESS'
               AND created_at >= date_trunc('day', now() AT TIME ZONE 'Asia/Kolkata') AT TIME ZONE 'Asia/Kolkata'
            """;

    private final JdbcTemplate jdbc;
    private final VehicleRepository vehicles;
    private final RedisJsonCache cache;
    private final ZoneId tz;

    public AnalyticsService(JdbcTemplate jdbc, VehicleRepository vehicles, RedisJsonCache cache, AppProperties props) {
        this.jdbc = jdbc;
        this.vehicles = vehicles;
        this.cache = cache;
        this.tz = ZoneId.of(props.timezone());
    }

    public record TypeUtilization(String type, int fleet, int utilization) {}

    public record TrendPoint(String date, int utilization, BigDecimal revenue) {}

    public record UtilizationResponse(long fleetSize, long rented, long inMaintenance, double utilizationRate,
                                      BigDecimal revenueToday, List<TypeUtilization> byType,
                                      List<TrendPoint> trend) {}

    public UtilizationResponse utilization() {
        return cache.get("analytics:utilization", new TypeReference<UtilizationResponse>() {},
                Duration.ofMinutes(1), this::compute);
    }

    private UtilizationResponse compute() {
        long fleet = vehicles.count();
        long rented = vehicles.countByStatus(VehicleStatus.RENTED);
        long maintenance = vehicles.countByStatus(VehicleStatus.MAINTENANCE);
        double rate = Money.round2((double) rented / Math.max(1, fleet - maintenance));

        List<TypeUtilization> byType = jdbc.query(BY_TYPE_SQL, (rs, i) -> {
            int n = rs.getInt("fleet");
            double hours = rs.getDouble("booked_hours");
            return new TypeUtilization(rs.getString("type"), n, pct(hours, n * 7 * 24.0));
        });

        List<TrendPoint> trend = new ArrayList<>();
        List<Object[]> rows = jdbc.query(TREND_SQL, (rs, i) -> new Object[] {
                rs.getString("label"), rs.getDouble("booked_hours"), rs.getBigDecimal("revenue")});
        double hoursToday = Math.max(1, LocalTime.now(tz).toSecondOfDay() / 3600.0);
        for (int i = 0; i < rows.size(); i++) {
            Object[] r = rows.get(i);
            double dayHours = i == rows.size() - 1 ? hoursToday : 24.0;
            trend.add(new TrendPoint((String) r[0], pct((Double) r[1], fleet * dayHours), Money.rupees((BigDecimal) r[2])));
        }

        BigDecimal revenueToday = jdbc.queryForObject(REVENUE_TODAY_SQL, BigDecimal.class);
        return new UtilizationResponse(fleet, rented, maintenance, rate,
                Money.rupees(revenueToday == null ? BigDecimal.ZERO : revenueToday), byType, trend);
    }

    private static int pct(double used, double available) {
        return available <= 0 ? 0 : (int) Math.round(Math.min(100, used / available * 100));
    }
}
