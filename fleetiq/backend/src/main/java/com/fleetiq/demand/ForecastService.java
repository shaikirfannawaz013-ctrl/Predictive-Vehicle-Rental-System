package com.fleetiq.demand;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fleetiq.booking.BookingRepository;
import com.fleetiq.booking.BookingStatus;
import com.fleetiq.common.RedisJsonCache;
import com.fleetiq.config.AppProperties;
import com.fleetiq.fleet.VehicleType;
import com.fleetiq.ml.MlClient;
import com.fleetiq.ml.MlDtos.ForecastDay;
import com.fleetiq.ml.MlDtos.ForecastRequest;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Service;

/** 7-day booking forecast per vehicle type, cached 30 minutes. */
@Service
public class ForecastService {

    private static final String CACHE_KEY = "demand:forecast";
    private final BookingRepository bookings;
    private final MlClient ml;
    private final RedisJsonCache cache;
    private final ZoneId tz;

    public ForecastService(BookingRepository bookings, MlClient ml, RedisJsonCache cache, AppProperties props) {
        this.bookings = bookings;
        this.ml = ml;
        this.cache = cache;
        this.tz = ZoneId.of(props.timezone());
    }

    /** rows: [{day: "Mon", date: "2026-09-28", SUV: 18, SEDAN: 12, ...}] */
    public record Forecast(List<Map<String, Object>> rows, String modelVersion) {}

    public Forecast nextWeek() {
        return cache.get(CACHE_KEY, new TypeReference<Forecast>() {}, Duration.ofMinutes(30), this::compute);
    }

    private Forecast compute() {
        Map<String, Double> avg = new LinkedHashMap<>();
        for (VehicleType t : VehicleType.values()) {
            avg.put(t.name(), 0.0);
        }
        for (Object[] row : bookings.countPerTypeSince(BookingStatus.REALISED,
                Instant.now().minus(Duration.ofDays(28)))) {
            avg.put(((VehicleType) row[0]).name(), ((Number) row[1]).doubleValue() / 28.0);
        }
        LocalDate start = LocalDate.now(tz).plusDays(1);
        var fromModel = ml.forecast(new ForecastRequest(start.toString(), 7, avg));
        if (fromModel.isPresent()) {
            List<Map<String, Object>> rows = new ArrayList<>();
            for (ForecastDay d : fromModel.get().days()) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("day", d.day());
                row.put("date", d.date());
                row.putAll(d.bookings());
                rows.add(row);
            }
            return new Forecast(rows, fromModel.get().modelVersion());
        }
        return new Forecast(fallback(start, avg), "rule-based-fallback");
    }

    /** Weekly seasonality: Fri-Sun pilgrim and tourist traffic lifts SUVs most. */
    private static List<Map<String, Object>> fallback(LocalDate start, Map<String, Double> avg) {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (int i = 0; i < 7; i++) {
            LocalDate date = start.plusDays(i);
            DayOfWeek dow = date.getDayOfWeek();
            boolean weekend = dow == DayOfWeek.FRIDAY || dow == DayOfWeek.SATURDAY || dow == DayOfWeek.SUNDAY;
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("day", dow.getDisplayName(TextStyle.SHORT, Locale.ENGLISH));
            row.put("date", date.toString());
            avg.forEach((type, base) -> {
                double factor = weekend ? ("SUV".equals(type) ? 1.8 : 1.35) : 1.0;
                row.put(type, (int) Math.round(Math.max(base, 0.5) * factor));
            });
            rows.add(row);
        }
        return rows;
    }
}
