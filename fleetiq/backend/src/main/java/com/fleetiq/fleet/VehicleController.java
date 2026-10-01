package com.fleetiq.fleet;

import com.fleetiq.fleet.VehicleDtos.CreateVehicleRequest;
import com.fleetiq.fleet.VehicleDtos.VehicleResponse;
import com.fleetiq.user.AuthUser;
import jakarta.validation.Valid;
import java.time.Instant;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/vehicles")
public class VehicleController {

    private final VehicleService vehicles;

    public VehicleController(VehicleService vehicles) {
        this.vehicles = vehicles;
    }

    @GetMapping("/search")
    public List<VehicleResponse> search(@RequestParam(required = false) String zoneId,
                                        @RequestParam(required = false) VehicleType type,
                                        @RequestParam Instant startTime,
                                        @RequestParam Instant endTime,
                                        @AuthenticationPrincipal AuthUser user) {
        return vehicles.search(blankToNull(zoneId), type, startTime, endTime, user.id());
    }

    @GetMapping("/{id}")
    public VehicleResponse get(@PathVariable Long id) {
        return vehicles.get(id);
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public List<VehicleResponse> all() {
        return vehicles.all();
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.CREATED)
    public VehicleResponse create(@Valid @RequestBody CreateVehicleRequest req) {
        return vehicles.create(req);
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s;
    }
}
