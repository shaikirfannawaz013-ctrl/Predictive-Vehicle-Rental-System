package com.fleetiq.demand;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fleetiq.booking.BookingRepository;
import com.fleetiq.booking.BookingStatus;
import com.fleetiq.common.ApiException;
import com.fleetiq.common.Money;
import com.fleetiq.common.RedisJsonCache;
import com.fleetiq.config.AppProperties;
import com.fleetiq.demand.DemandMessages.ZoneDemand;
import com.fleetiq.fleet.VehicleRepository;
import com.fleetiq.fleet.VehicleStatus;
import com.fleetiq.fleet.Zone;
import com.fleetiq.fleet.ZoneRepository;
import com.fleetiq.ml.MlClient;
import com.fleetiq.ml.MlDtos.DemandFeatures;
import com.fleetiq.ml.MlDtos.DemandPrediction;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Sort;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

/**
 * Live demand per zone. Inputs: searches in the last hour (Redis sorted sets fed from Kafka),
 * vehicles parked in the zone, and bookings starting in the next 24 h. The ML service turns these
 * into a 0-1 demand score; the pricing multiplier is derived from that score.
 *
 * Redis keys:
 *   demand:searches:{zoneId}          ZSET of search ids scored by epoch millis (ALL = no zone chosen)
 *   demand:searches:{zoneId}:{type}   same, per vehicle type (used for allocation)
 *   demand:zones                      cached List<ZoneDemand>, 30 s TTL
 */
@Service
public class DemandService {

    private static final Logger log = LoggerFactory.getLogger(DemandService.class);
    public static final String SEARCH_KEY = "demand:searches:";
    public static final String ANY_ZONE = "ALL";
    private static final String CACHE_KEY = "demand:zones";
    private static final long HOUR_MS = 3_600_000L;

    private final ZoneRepository zones;
    private final VehicleRepository vehicles;
    private final BookingRepository bookings;
    private final StringRedisTemplate redis;
    private final RedisJsonCache cache;
    private final MlClient ml;
    private final AppProperties props;
    private final ZoneId tz;

    public DemandService(ZoneRepository zones, VehicleRepository vehicles, BookingRepository bookings,
                         StringRedisTemplate redis, RedisJsonCache cache, MlClient ml, AppProperties props) {
        this.zones = zones;
        this.vehicles = vehicles;
        this.bookings = bookings;
        this.redis = redis;
        this.cache = cache;
        this.ml = ml;
        this.props = props;
        this.tz = ZoneId.of(props.timezone());
    }

    public List<ZoneDemand> allZones() {
        return cache.get(CACHE_KEY, new TypeReference<List<ZoneDemand>>() {}, Duration.ofSeconds(30), this::compute);
    }

    public ZoneDemand forZone(String zoneId) {
        return allZones().stream().filter(z -> z.id().equals(zoneId)).findFirst()
                .orElseThrow(() -> ApiException.notFound("Unknown pickup area: " + zoneId));
    }

    public Map<String, ZoneDemand> byZoneId() {
        return allZones().stream().collect(Collectors.toMap(ZoneDemand::id, z -> z));
    }

    public void invalidate() {
        cache.evict(CACHE_KEY);
    }

    /** Called by the Kafka consumer for each search event. */
    public void recordSearch(String zoneId, String vehicleType, Instant at) {
        String zoneKey = SEARCH_KEY + (zoneId == null ? ANY_ZONE : zoneId);
        addToWindow(zoneKey, at);
        if (vehicleType != null) {
            addToWindow(zoneKey + ":" + vehicleType, at);
        }
    }

    public int searchesLastHour(String zoneId) {
        return countWindow(SEARCH_KEY + zoneId);
    }

    public int searchesLastHour(String zoneId, String vehicleType) {
        return countWindow(SEARCH_KEY + zoneId + ":" + vehicleType);
    }

    public double multiplierFor(double demand) {
        AppProperties.Pricing p = props.pricing();
        double m = 1 + Math.max(0, demand - p.surgeThreshold()) * p.surgeSlope();
        return Money.round2(Math.min(m, p.maxMultiplier()));
    }

    /** Fallback when the ML service is unavailable: saturating curve of pressure on the zone. */
    static double heuristicDemand(DemandFeatures f) {
        double pressure = (f.searchesLastHour() + 2.0 * f.upcomingBookings()) / (f.availableSupply() + 1.0);
        double demand = 1 - Math.exp(-pressure / 2.0);
        if (f.weekend()) {
            demand += 0.05;
        }
        return Money.clamp(demand, 0, 1);
    }

    private List<ZoneDemand> compute() {
        Instant now = Instant.now();
        List<Zone> all = zones.findAll(Sort.by("id"));
        Map<String, Long> supply = toCountMap(vehicles.countByZoneAndStatus(VehicleStatus.AVAILABLE));
        Map<String, Long> upcoming = toCountMap(bookings.countStartingByZone(
                BookingStatus.OCCUPYING, now, now.plus(Duration.ofHours(24))));
        int unzonedShare = searchesLastHour(ANY_ZONE) / Math.max(1, all.size());

        ZonedDateTime local = now.atZone(tz);
        boolean weekend = local.getDayOfWeek() == DayOfWeek.SATURDAY || local.getDayOfWeek() == DayOfWeek.SUNDAY;

        List<DemandFeatures> features = new ArrayList<>();
        for (Zone z : all) {
            features.add(new DemandFeatures(z.getId(),
                    searchesLastHour(z.getId()) + unzonedShare,
                    supply.getOrDefault(z.getId(), 0L).intValue(),
                    upcoming.getOrDefault(z.getId(), 0L).intValue(),
                    local.getHour(), local.getDayOfWeek().getValue(), weekend));
        }

        Map<String, Double> predicted = new HashMap<>();
        ml.predictDemand(features).ifPresent(r -> {
            for (DemandPrediction p : r.predictions()) {
                predicted.put(p.zoneId(), p.demand());
            }
        });

        List<ZoneDemand> result = new ArrayList<>();
        for (int i = 0; i < all.size(); i++) {
            Zone z = all.get(i);
            DemandFeatures f = features.get(i);
            double demand = Money.round2(Money.clamp(
                    predicted.getOrDefault(z.getId(), heuristicDemand(f)), 0, 1));
            result.add(new ZoneDemand(z.getId(), z.getName(), z.getMapX(), z.getMapY(), z.getLatitude(),
                    z.getLongitude(), demand, f.availableSupply(), f.searchesLastHour(), f.upcomingBookings(),
                    multiplierFor(demand)));
        }
        return result;
    }

    private void addToWindow(String key, Instant at) {
        try {
            long ts = at.toEpochMilli();
            redis.opsForZSet().add(key, UUID.randomUUID().toString(), ts);
            redis.opsForZSet().removeRangeByScore(key, 0, ts - 2 * HOUR_MS);
            redis.expire(key, Duration.ofHours(3));
        } catch (Exception e) {
            log.warn("Could not record search in Redis: {}", e.getMessage());
        }
    }

    private int countWindow(String key) {
        try {
            long now = System.currentTimeMillis();
            Long n = redis.opsForZSet().count(key, now - HOUR_MS, now);
            return n == null ? 0 : n.intValue();
        } catch (Exception e) {
            return 0;
        }
    }

    private static Map<String, Long> toCountMap(List<Object[]> rows) {
        Map<String, Long> map = new HashMap<>();
        for (Object[] row : rows) {
            map.put((String) row[0], ((Number) row[1]).longValue());
        }
        return map;
    }
}
