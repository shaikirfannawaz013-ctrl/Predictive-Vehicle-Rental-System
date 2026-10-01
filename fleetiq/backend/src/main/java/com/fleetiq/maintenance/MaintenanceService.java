package com.fleetiq.maintenance;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fleetiq.booking.BookingRepository;
import com.fleetiq.booking.BookingStatus;
import com.fleetiq.common.ApiException;
import com.fleetiq.common.Money;
import com.fleetiq.common.RedisJsonCache;
import com.fleetiq.demand.DemandService;
import com.fleetiq.fleet.Vehicle;
import com.fleetiq.fleet.VehicleRepository;
import com.fleetiq.fleet.VehicleStatus;
import com.fleetiq.ml.MlClient;
import com.fleetiq.ml.MlDtos.MaintenanceFeatures;
import com.fleetiq.ml.MlDtos.MaintenancePrediction;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MaintenanceService {

    private static final String CACHE_KEY = "maintenance:predictions";

    private final VehicleRepository vehicles;
    private final BookingRepository bookings;
    private final MaintenanceRepository records;
    private final MlClient ml;
    private final RedisJsonCache cache;
    private final DemandService demand;

    public MaintenanceService(VehicleRepository vehicles, BookingRepository bookings, MaintenanceRepository records,
                              MlClient ml, RedisJsonCache cache, DemandService demand) {
        this.vehicles = vehicles;
        this.bookings = bookings;
        this.records = records;
        this.ml = ml;
        this.cache = cache;
        this.demand = demand;
    }

    public record MaintenancePredictionResponse(Long vehicleId, String vehicleName, String registrationNumber,
                                                VehicleStatus status, int odometer, int healthScore,
                                                double failureProbability, String component, int dueInDays) {}

    public record ScheduleResponse(Long vehicleId, String status) {}

    /** Health predictions for the whole fleet, worst first. Cached 10 minutes. */
    public List<MaintenancePredictionResponse> predictions() {
        return cache.get(CACHE_KEY, new TypeReference<List<MaintenancePredictionResponse>>() {},
                Duration.ofMinutes(10), this::compute);
    }

    public void invalidate() {
        cache.evict(CACHE_KEY);
    }

    @Transactional
    public ScheduleResponse schedule(Long vehicleId) {
        Vehicle v = vehicles.findByIdForUpdate(vehicleId)
                .orElseThrow(() -> ApiException.notFound("Vehicle not found."));
        if (v.getStatus() == VehicleStatus.RENTED) {
            throw ApiException.conflict(v.getName() + " is on rent. Schedule the service after it's returned.");
        }
        if (records.findByVehicleIdAndStatus(vehicleId, MaintenanceRecord.SCHEDULED).isEmpty()) {
            MaintenancePredictionResponse p = predictions().stream()
                    .filter(x -> x.vehicleId().equals(vehicleId)).findFirst().orElse(null);
            MaintenanceRecord r = new MaintenanceRecord();
            r.setVehicle(v);
            r.setComponent(p == null ? "General service" : p.component());
            r.setPredictedFailureProbability(p == null ? null : p.failureProbability());
            r.setScheduledFor(LocalDate.now());
            r.setStatus(MaintenanceRecord.SCHEDULED);
            records.save(r);
        }
        v.setStatus(VehicleStatus.MAINTENANCE);
        invalidate();
        demand.invalidate();
        return new ScheduleResponse(vehicleId, MaintenanceRecord.SCHEDULED);
    }

    @Transactional
    public ScheduleResponse complete(Long vehicleId) {
        Vehicle v = vehicles.findByIdForUpdate(vehicleId)
                .orElseThrow(() -> ApiException.notFound("Vehicle not found."));
        for (MaintenanceRecord r : records.findByVehicleIdAndStatus(vehicleId, MaintenanceRecord.SCHEDULED)) {
            r.setStatus(MaintenanceRecord.COMPLETED);
            r.setCompletedAt(Instant.now());
        }
        v.setStatus(VehicleStatus.AVAILABLE);
        v.setLastServiceDate(LocalDate.now());
        v.setLastServiceOdometer(v.getOdometerKm());
        v.setHealthScore(100);
        invalidate();
        demand.invalidate();
        return new ScheduleResponse(vehicleId, MaintenanceRecord.COMPLETED);
    }

    /** Fallback when the ML service is down: wear grows with km and days since the last service. */
    static MaintenancePrediction heuristic(MaintenanceFeatures f) {
        double p = 0.03 + f.kmSinceService() / 12_000.0 * 0.45 + f.daysSinceService() / 240.0 * 0.25
                + f.odometerKm() / 200_000.0 * 0.2 + f.tripsLast30Days() * 0.004;
        p = Money.round2(Money.clamp(p, 0.01, 0.98));
        String component;
        if (f.electric()) {
            component = f.odometerKm() > 20_000 ? "Battery cooling" : "Tyres";
        } else if (f.kmSinceService() > 9_000) {
            component = "Engine oil";
        } else if (f.odometerKm() > 90_000) {
            component = "Brake pads";
        } else if (f.daysSinceService() > 120) {
            component = "Clutch plate";
        } else {
            component = "Tyres";
        }
        int due = (int) Math.max(1, Math.round((1 - p) * 45));
        return new MaintenancePrediction(f.vehicleId(), p, (int) Math.round((1 - p) * 100), component, due);
    }

    private List<MaintenancePredictionResponse> compute() {
        List<Vehicle> fleet = vehicles.findAllWithZone();
        Instant since = Instant.now().minus(Duration.ofDays(30));
        Map<Long, Long> trips = new HashMap<>();
        for (Object[] row : bookings.countTripsPerVehicleSince(BookingStatus.REALISED, since)) {
            trips.put((Long) row[0], ((Number) row[1]).longValue());
        }
        LocalDate today = LocalDate.now();
        List<MaintenanceFeatures> features = fleet.stream().map(v -> new MaintenanceFeatures(v.getId(),
                v.getOdometerKm(),
                Math.max(0, v.getOdometerKm() - v.getLastServiceOdometer()),
                (int) ChronoUnit.DAYS.between(v.getLastServiceDate(), today),
                (int) ChronoUnit.MONTHS.between(v.getPurchaseDate(), today),
                trips.getOrDefault(v.getId(), 0L).intValue(),
                v.isElectric())).toList();

        Map<Long, MaintenancePrediction> predicted = new HashMap<>();
        ml.predictMaintenance(features).ifPresent(r -> r.predictions().forEach(p -> predicted.put(p.vehicleId(), p)));

        Map<Long, Vehicle> byId = new HashMap<>();
        fleet.forEach(v -> byId.put(v.getId(), v));
        return features.stream()
                .map(f -> {
                    MaintenancePrediction p = predicted.getOrDefault(f.vehicleId(), heuristic(f));
                    Vehicle v = byId.get(f.vehicleId());
                    return new MaintenancePredictionResponse(v.getId(), v.getName(), v.getRegistrationNumber(),
                            v.getStatus(), v.getOdometerKm(), p.healthScore(), p.failureProbability(),
                            p.component(), p.dueInDays());
                })
                .sorted(Comparator.comparingInt(MaintenancePredictionResponse::healthScore))
                .toList();
    }
}
