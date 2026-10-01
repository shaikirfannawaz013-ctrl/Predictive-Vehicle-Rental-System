package com.fleetiq.fleet;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;

public final class VehicleDtos {
    private VehicleDtos() {}

    public record VehicleResponse(Long id, String registrationNumber, String name, VehicleType type, int seats,
                                  String fuel, String transmission, BigDecimal baseRate, BigDecimal currentRate,
                                  double demandMultiplier, String zoneId, String zoneName,
                                  Double availabilityProbability, VehicleStatus status, int odometer,
                                  Integer healthScore) {}

    public record CreateVehicleRequest(
            @NotBlank String registrationNumber,
            @NotBlank String name,
            @NotNull VehicleType type,
            @Min(1) @Max(12) int seats,
            @NotBlank String fuel,
            @NotBlank String transmission,
            @NotNull @Positive BigDecimal baseRate,
            @NotBlank String zoneId,
            @Min(0) int odometerKm) {}
}
