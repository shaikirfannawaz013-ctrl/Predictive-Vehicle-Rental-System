package com.fleetiq.pricing;

import com.fleetiq.common.ApiException;
import com.fleetiq.common.Money;
import com.fleetiq.config.AppProperties;
import com.fleetiq.demand.DemandMessages.ZoneDemand;
import com.fleetiq.demand.DemandService;
import com.fleetiq.fleet.Vehicle;
import com.fleetiq.fleet.VehicleRepository;
import com.fleetiq.risk.RiskService;
import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PricingService {

    private static final Set<DayOfWeek> WEEKEND = Set.of(DayOfWeek.FRIDAY, DayOfWeek.SATURDAY, DayOfWeek.SUNDAY);

    private final VehicleRepository vehicles;
    private final DemandService demand;
    private final RiskService risk;
    private final AppProperties.Pricing cfg;
    private final ZoneId tz;

    public PricingService(VehicleRepository vehicles, DemandService demand, RiskService risk, AppProperties props) {
        this.vehicles = vehicles;
        this.demand = demand;
        this.risk = risk;
        this.cfg = props.pricing();
        this.tz = ZoneId.of(props.timezone());
    }

    public record Quote(int days, BigDecimal baseRate, double multiplier, BigDecimal subtotal, BigDecimal tax,
                        BigDecimal total, BigDecimal deposit, List<String> reasons) {}

    @Transactional(readOnly = true)
    public Quote quote(Long vehicleId, Instant start, Instant end, Long customerId) {
        Vehicle v = vehicles.findWithZone(vehicleId)
                .orElseThrow(() -> ApiException.notFound("Vehicle not found."));
        return quote(v, start, end, customerId);
    }

    public Quote quote(Vehicle v, Instant start, Instant end, Long customerId) {
        validateRange(start, end);
        int days = rentalDays(start, end);
        ZoneDemand zone = demand.forZone(v.getZone().getId());

        List<String> reasons = new ArrayList<>();
        double multiplier = zone.multiplier();
        if (zone.demand() >= 0.6) {
            reasons.add(zone.searches() + " people searched near " + zone.name() + " in the last hour");
        } else if (multiplier > 1.0) {
            reasons.add("Demand near " + zone.name() + " is above normal");
        }
        if (WEEKEND.contains(start.atZone(tz).getDayOfWeek())) {
            multiplier += cfg.weekendPremium();
            reasons.add("Weekend pickup");
        }
        multiplier = Money.round2(Math.min(multiplier, cfg.maxMultiplier()));

        BigDecimal subtotal = Money.times(v.getBaseRate().multiply(BigDecimal.valueOf(days)), multiplier);
        BigDecimal tax = Money.times(subtotal, cfg.taxRate());
        BigDecimal deposit = cfg.deposit();
        if (customerId != null && risk.isHighRisk(customerId)) {
            deposit = Money.times(deposit, cfg.highRiskDepositFactor());
        }
        return new Quote(days, Money.rupees(v.getBaseRate()), multiplier, subtotal, tax, subtotal.add(tax),
                Money.rupees(deposit), reasons);
    }

    /** Price per day right now, used in search results. */
    public BigDecimal currentDailyRate(Vehicle v, double zoneMultiplier) {
        return Money.times(v.getBaseRate(), zoneMultiplier);
    }

    static int rentalDays(Instant start, Instant end) {
        long hours = (long) Math.ceil(Duration.between(start, end).toMinutes() / 60.0);
        return (int) Math.max(1, Math.ceil(hours / 24.0));
    }

    public static void validateRange(Instant start, Instant end) {
        if (start == null || end == null || !end.isAfter(start)) {
            throw ApiException.badRequest("Return time must be after pickup time.");
        }
        if (start.isBefore(Instant.now().minus(Duration.ofHours(1)))) {
            throw ApiException.badRequest("Pickup time is in the past. Choose a later time.");
        }
        if (Duration.between(start, end).toDays() > 30) {
            throw ApiException.badRequest("Bookings can be at most 30 days long.");
        }
    }
}
