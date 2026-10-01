package com.fleetiq.fleet;

import com.fleetiq.booking.BookingRepository;
import com.fleetiq.booking.BookingStatus;
import com.fleetiq.common.ApiException;
import com.fleetiq.common.EventPublisher;
import com.fleetiq.common.Money;
import com.fleetiq.config.AppProperties;
import com.fleetiq.demand.DemandMessages.SearchEventMessage;
import com.fleetiq.demand.DemandMessages.ZoneDemand;
import com.fleetiq.demand.DemandService;
import com.fleetiq.fleet.VehicleDtos.CreateVehicleRequest;
import com.fleetiq.fleet.VehicleDtos.VehicleResponse;
import com.fleetiq.pricing.PricingService;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class VehicleService {

    private final VehicleRepository vehicles;
    private final ZoneRepository zones;
    private final BookingRepository bookings;
    private final DemandService demand;
    private final PricingService pricing;
    private final EventPublisher events;
    private final AppProperties props;

    public VehicleService(VehicleRepository vehicles, ZoneRepository zones, BookingRepository bookings,
                          DemandService demand, PricingService pricing, EventPublisher events, AppProperties props) {
        this.vehicles = vehicles;
        this.zones = zones;
        this.bookings = bookings;
        this.demand = demand;
        this.pricing = pricing;
        this.events = events;
        this.props = props;
    }

    /**
     * Vehicles free for [start, end), priced at the zone's live multiplier. The search itself is published
     * to Kafka: that stream is what makes demand (and prices) rise when many people look at one area.
     */
    @Transactional(readOnly = true)
    public List<VehicleResponse> search(String zoneId, VehicleType type, Instant start, Instant end, Long userId) {
        PricingService.validateRange(start, end);
        Instant now = Instant.now();
        Set<Long> blocked = new HashSet<>(bookings.findBlockedVehicleIds(
                start, end, BookingStatus.OCCUPYING, BookingStatus.PENDING_PAYMENT, now));
        Map<String, ZoneDemand> demandByZone = demand.byZoneId();

        List<VehicleResponse> result = vehicles.findAllWithZone().stream()
                .filter(v -> v.getStatus() != VehicleStatus.MAINTENANCE)
                .filter(v -> zoneId == null || v.getZone().getId().equals(zoneId))
                .filter(v -> type == null || v.getType() == type)
                .filter(v -> !blocked.contains(v.getId()))
                .map(v -> toResponse(v, demandByZone.get(v.getZone().getId()), true))
                .sorted(Comparator.comparing(VehicleResponse::currentRate))
                .toList();

        SearchEventMessage event = new SearchEventMessage(userId, zoneId, type == null ? null : type.name(),
                start, end, now);
        events.publish(props.topics().vehicleSearches(), zoneId == null ? DemandService.ANY_ZONE : zoneId, event,
                () -> demand.recordSearch(event.zoneId(), event.vehicleType(), event.searchedAt()));
        return result;
    }

    @Transactional(readOnly = true)
    public VehicleResponse get(Long id) {
        Vehicle v = vehicles.findWithZone(id).orElseThrow(() -> ApiException.notFound("Vehicle not found."));
        return toResponse(v, demand.forZone(v.getZone().getId()), false);
    }

    @Transactional(readOnly = true)
    public List<VehicleResponse> all() {
        Map<String, ZoneDemand> demandByZone = demand.byZoneId();
        return vehicles.findAllWithZone().stream()
                .map(v -> toResponse(v, demandByZone.get(v.getZone().getId()), false))
                .toList();
    }

    @Transactional
    public VehicleResponse create(CreateVehicleRequest req) {
        Zone zone = zones.findById(req.zoneId()).orElseThrow(() -> ApiException.badRequest("Unknown zone."));
        Vehicle v = new Vehicle();
        v.setRegistrationNumber(req.registrationNumber().toUpperCase());
        v.setName(req.name());
        v.setType(req.type());
        v.setSeats(req.seats());
        v.setFuel(req.fuel());
        v.setTransmission(req.transmission());
        v.setBaseRate(req.baseRate());
        v.setZone(zone);
        v.setStatus(VehicleStatus.AVAILABLE);
        v.setOdometerKm(req.odometerKm());
        v.setPurchaseDate(LocalDate.now());
        v.setLastServiceDate(LocalDate.now());
        v.setLastServiceOdometer(req.odometerKm());
        v.setHealthScore(100);
        vehicles.save(v);
        demand.invalidate();
        return toResponse(v, demand.forZone(zone.getId()), false);
    }

    private VehicleResponse toResponse(Vehicle v, ZoneDemand zd, boolean withAvailability) {
        double multiplier = zd == null ? 1.0 : zd.multiplier();
        Double availability = null;
        if (withAvailability && zd != null) {
            // Chance the vehicle is still free if the customer waits: falls as demand outruns supply.
            double pressure = zd.demand() * (1 + zd.searches() / (zd.supply() * 4.0 + 4.0));
            availability = Money.round2(Money.clamp(1 - pressure * 0.6, 0.1, 0.97));
        }
        return new VehicleResponse(v.getId(), v.getRegistrationNumber(), v.getName(), v.getType(), v.getSeats(),
                v.getFuel(), v.getTransmission(), Money.rupees(v.getBaseRate()),
                pricing.currentDailyRate(v, multiplier), multiplier, v.getZone().getId(), v.getZone().getName(),
                availability, v.getStatus(), v.getOdometerKm(), v.getHealthScore());
    }
}
