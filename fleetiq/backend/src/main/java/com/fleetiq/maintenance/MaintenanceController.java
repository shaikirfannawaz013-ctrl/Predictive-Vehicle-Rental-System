package com.fleetiq.maintenance;

import com.fleetiq.maintenance.MaintenanceService.ScheduleResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/maintenance")
@PreAuthorize("hasRole('ADMIN')")
public class MaintenanceController {

    private final MaintenanceService maintenance;

    public MaintenanceController(MaintenanceService maintenance) {
        this.maintenance = maintenance;
    }

    @PostMapping("/{vehicleId}/schedule")
    public ScheduleResponse schedule(@PathVariable Long vehicleId) {
        return maintenance.schedule(vehicleId);
    }

    @PostMapping("/{vehicleId}/complete")
    public ScheduleResponse complete(@PathVariable Long vehicleId) {
        return maintenance.complete(vehicleId);
    }
}
