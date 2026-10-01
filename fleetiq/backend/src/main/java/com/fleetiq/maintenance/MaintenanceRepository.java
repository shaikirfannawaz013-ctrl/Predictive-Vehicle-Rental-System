package com.fleetiq.maintenance;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MaintenanceRepository extends JpaRepository<MaintenanceRecord, Long> {
    List<MaintenanceRecord> findByVehicleIdAndStatus(Long vehicleId, String status);
}
