package com.fleetiq.demand;

import com.fleetiq.demand.DemandMessages.ZoneDemand;
import com.fleetiq.maintenance.MaintenanceService;
import com.fleetiq.maintenance.MaintenanceService.MaintenancePredictionResponse;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/predictions")
public class PredictionController {

    private final DemandService demand;
    private final ForecastService forecast;
    private final MaintenanceService maintenance;

    public PredictionController(DemandService demand, ForecastService forecast, MaintenanceService maintenance) {
        this.demand = demand;
        this.forecast = forecast;
        this.maintenance = maintenance;
    }

    public record DemandOverview(List<ZoneDemand> zones, List<Map<String, Object>> forecast, String modelVersion,
                                 Instant generatedAt) {}

    /** Open to all signed-in users: customers see zone prices on the search page. */
    @GetMapping("/demand")
    public DemandOverview demand() {
        ForecastService.Forecast f = forecast.nextWeek();
        return new DemandOverview(demand.allZones(), f.rows(), f.modelVersion(), Instant.now());
    }

    @GetMapping("/maintenance")
    @PreAuthorize("hasRole('ADMIN')")
    public List<MaintenancePredictionResponse> maintenance() {
        return maintenance.predictions();
    }
}
