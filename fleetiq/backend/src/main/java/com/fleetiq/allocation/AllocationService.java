package com.fleetiq.allocation;

import com.fleetiq.common.ApiException;
import com.fleetiq.demand.DemandMessages.ZoneDemand;
import com.fleetiq.demand.DemandService;
import com.fleetiq.fleet.Vehicle;
import com.fleetiq.fleet.VehicleRepository;
import com.fleetiq.fleet.VehicleStatus;
import com.fleetiq.fleet.VehicleType;
import com.fleetiq.fleet.ZoneRepository;
import com.fleetiq.notification.NotificationService;
import com.fleetiq.notification.NotificationType;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Location-based allocation. Greedy rebalancing: take idle vehicles from low-demand zones and
 * move them to zones where searches far outnumber parked vehicles, preferring the vehicle type
 * people are searching for there.
 */
@Service
public class AllocationService {

    private static final int MAX_MOVES = 6;
    private static final int MAX_PER_TARGET = 2;
    private static final int MIN_LEFT_IN_SOURCE = 2;

    private final DemandService demand;
    private final VehicleRepository vehicles;
    private final ZoneRepository zones;
    private final AllocationMoveRepository moves;
    private final NotificationService notifications;

    public AllocationService(DemandService demand, VehicleRepository vehicles, ZoneRepository zones,
                             AllocationMoveRepository moves, NotificationService notifications) {
        this.demand = demand;
        this.vehicles = vehicles;
        this.zones = zones;
        this.moves = moves;
        this.notifications = notifications;
    }

    public record MoveResponse(Long id, Long vehicleId, String vehicleName, VehicleType type, String fromZone,
                               String toZone, BigDecimal expectedGain, String reason, String status) {
        static MoveResponse from(AllocationMove m) {
            return new MoveResponse(m.getId(), m.getVehicle().getId(), m.getVehicle().getName(),
                    m.getVehicle().getType(), m.getFromZone().getName(), m.getToZone().getName(),
                    m.getExpectedGain(), m.getReason(), m.getStatus());
        }
    }

    public record ApplyResponse(Long id, String status) {}

    @Transactional
    public List<MoveResponse> recommendations() {
        moves.deleteByStatus(AllocationMove.SUGGESTED);
        List<ZoneDemand> all = demand.allZones();

        List<ZoneDemand> targets = all.stream()
                .filter(z -> z.demand() >= 0.6 && z.searches() > z.supply() * 1.5)
                .sorted(Comparator.comparingDouble(ZoneDemand::demand).reversed())
                .toList();
        List<ZoneDemand> sources = all.stream()
                .filter(z -> z.demand() < 0.45 && z.supply() > MIN_LEFT_IN_SOURCE)
                .sorted(Comparator.comparingDouble(ZoneDemand::demand))
                .toList();
        if (targets.isEmpty() || sources.isEmpty()) {
            return List.of();
        }

        Map<String, List<Vehicle>> idleByZone = vehicles.findAllWithZone().stream()
                .filter(v -> v.getStatus() == VehicleStatus.AVAILABLE)
                .collect(Collectors.groupingBy(v -> v.getZone().getId()));
        Map<String, Integer> remaining = new HashMap<>();
        sources.forEach(s -> remaining.put(s.id(), idleByZone.getOrDefault(s.id(), List.of()).size()));
        Set<Long> used = new HashSet<>();
        List<AllocationMove> created = new ArrayList<>();

        for (ZoneDemand target : targets) {
            VehicleType wanted = mostSearchedType(target.id());
            for (int k = 0; k < MAX_PER_TARGET && created.size() < MAX_MOVES; k++) {
                Vehicle pick = null;
                ZoneDemand from = null;
                for (ZoneDemand source : sources) {
                    if (remaining.get(source.id()) <= MIN_LEFT_IN_SOURCE) {
                        continue;
                    }
                    List<Vehicle> idle = idleByZone.getOrDefault(source.id(), List.of()).stream()
                            .filter(v -> !used.contains(v.getId())).toList();
                    pick = idle.stream().filter(v -> wanted == null || v.getType() == wanted).findFirst()
                            .orElse(idle.isEmpty() ? null : idle.get(0));
                    if (pick != null) {
                        from = source;
                        break;
                    }
                }
                if (pick == null) {
                    break;
                }
                used.add(pick.getId());
                remaining.merge(from.id(), -1, Integer::sum);
                created.add(buildMove(pick, from, target, wanted));
            }
        }
        moves.saveAll(created);
        return created.stream()
                .sorted(Comparator.comparing(AllocationMove::getExpectedGain).reversed())
                .map(MoveResponse::from).toList();
    }

    @Transactional
    public ApplyResponse apply(Long id) {
        AllocationMove m = moves.findDetailed(id)
                .orElseThrow(() -> ApiException.notFound("This suggestion is out of date. Refresh the list."));
        if (!AllocationMove.SUGGESTED.equals(m.getStatus())) {
            return new ApplyResponse(id, m.getStatus());
        }
        Vehicle v = vehicles.findByIdForUpdate(m.getVehicle().getId()).orElseThrow();
        if (v.getStatus() != VehicleStatus.AVAILABLE) {
            throw ApiException.conflict(v.getName() + " is no longer idle. Refresh the suggestions.");
        }
        v.setZone(m.getToZone());
        m.setStatus(AllocationMove.DISPATCHED);
        demand.invalidate();
        notifications.notifyAdmins(NotificationType.BOOKING, "Driver dispatched: " + v.getName() + " moving from "
                + m.getFromZone().getName() + " to " + m.getToZone().getName() + ".");
        return new ApplyResponse(id, m.getStatus());
    }

    /**
     * Expected extra revenue over the next two days: rate x multiplier x chance of being booked,
     * in the target zone minus in the source zone.
     */
    private AllocationMove buildMove(Vehicle v, ZoneDemand from, ZoneDemand to, VehicleType wanted) {
        double rate = v.getBaseRate().doubleValue();
        double gain = 2 * (rate * to.multiplier() * to.demand() - rate * from.multiplier() * from.demand());
        String reason = wanted != null && v.getType() == wanted
                ? demand.searchesLastHour(to.id(), wanted.name()) + " " + label(wanted) + " searches in the last hour, "
                  + to.supply() + " vehicles parked there"
                : to.searches() + " searches in the last hour against " + to.supply() + " parked vehicles";

        AllocationMove m = new AllocationMove();
        m.setVehicle(v);
        m.setFromZone(zones.getReferenceById(from.id()));
        m.setToZone(zones.getReferenceById(to.id()));
        m.setExpectedGain(BigDecimal.valueOf(Math.max(0, gain)).setScale(-2, RoundingMode.HALF_UP).setScale(0));
        m.setReason(reason);
        m.setStatus(AllocationMove.SUGGESTED);
        return m;
    }

    private VehicleType mostSearchedType(String zoneId) {
        VehicleType best = null;
        int bestCount = 0;
        for (VehicleType t : VehicleType.values()) {
            int n = demand.searchesLastHour(zoneId, t.name());
            if (n > bestCount) {
                best = t;
                bestCount = n;
            }
        }
        return best;
    }

    private static String label(VehicleType t) {
        return switch (t) {
            case SUV -> "SUV";
            case SEDAN -> "sedan";
            case HATCHBACK -> "hatchback";
            case EV -> "electric car";
            case BIKE -> "two-wheeler";
        };
    }
}
