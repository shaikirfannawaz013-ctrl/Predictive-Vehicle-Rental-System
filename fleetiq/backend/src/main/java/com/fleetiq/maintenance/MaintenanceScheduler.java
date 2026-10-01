package com.fleetiq.maintenance;

import com.fleetiq.common.RedisJsonCache;
import com.fleetiq.fleet.VehicleRepository;
import com.fleetiq.fleet.VehicleStatus;
import com.fleetiq.maintenance.MaintenanceService.MaintenancePredictionResponse;
import com.fleetiq.notification.NotificationService;
import com.fleetiq.notification.NotificationType;
import java.time.Duration;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Refreshes fleet health every few hours and alerts fleet managers about likely failures (once a day each). */
@Component
public class MaintenanceScheduler {

    private static final double ALERT_THRESHOLD = 0.5;
    private final MaintenanceService maintenance;
    private final VehicleRepository vehicles;
    private final NotificationService notifications;
    private final RedisJsonCache cache;

    public MaintenanceScheduler(MaintenanceService maintenance, VehicleRepository vehicles,
                                NotificationService notifications, RedisJsonCache cache) {
        this.maintenance = maintenance;
        this.vehicles = vehicles;
        this.notifications = notifications;
        this.cache = cache;
    }

    @Scheduled(fixedDelay = 6 * 60 * 60 * 1000L, initialDelay = 60_000)
    @Transactional
    public void refreshHealth() {
        maintenance.invalidate();
        for (MaintenancePredictionResponse p : maintenance.predictions()) {
            vehicles.updateHealthScore(p.vehicleId(), p.healthScore());
            boolean atRisk = p.failureProbability() >= ALERT_THRESHOLD && p.status() != VehicleStatus.MAINTENANCE;
            if (atRisk && cache.claimOnce("maintenance:alerted:" + p.vehicleId(), Duration.ofHours(24))) {
                notifications.notifyAdmins(NotificationType.MAINTENANCE,
                        p.vehicleName() + " (" + p.registrationNumber() + "): " + p.component().toLowerCase()
                                + " likely to fail within " + p.dueInDays() + " days. Health score "
                                + p.healthScore() + ".");
            }
        }
    }
}
