package com.fleetiq.ml;

import java.util.List;
import java.util.Map;

/** Request/response shapes for the Python ML service (see ml-service/app/main.py). */
public final class MlDtos {
    private MlDtos() {}

    // ---- demand ----
    public record DemandFeatures(String zoneId, int searchesLastHour, int availableSupply, int upcomingBookings,
                                 int hour, int dayOfWeek, boolean weekend) {}
    public record DemandRequest(List<DemandFeatures> zones) {}
    public record DemandPrediction(String zoneId, double demand) {}
    public record DemandResponse(List<DemandPrediction> predictions, String modelVersion) {}

    // ---- 7-day booking forecast ----
    public record ForecastRequest(String startDate, int days, Map<String, Double> recentDailyAverage) {}
    public record ForecastDay(String date, String day, Map<String, Integer> bookings) {}
    public record ForecastResponse(List<ForecastDay> days, String modelVersion) {}

    // ---- predictive maintenance ----
    public record MaintenanceFeatures(Long vehicleId, int odometerKm, int kmSinceService, int daysSinceService,
                                      int ageMonths, int tripsLast30Days, boolean electric) {}
    public record MaintenanceRequest(List<MaintenanceFeatures> vehicles) {}
    public record MaintenancePrediction(Long vehicleId, double failureProbability, int healthScore,
                                        String component, int dueInDays) {}
    public record MaintenanceResponse(List<MaintenancePrediction> predictions, String modelVersion) {}

    // ---- customer risk ----
    public record RiskFeatures(Long customerId, int totalBookings, int lateReturns, int damageClaims,
                               int paymentFailures, double avgOverdueHours) {}
    public record RiskRequest(List<RiskFeatures> customers) {}
    public record RiskPrediction(Long customerId, double riskProbability, int score) {}
    public record RiskResponse(List<RiskPrediction> predictions, String modelVersion) {}
}
