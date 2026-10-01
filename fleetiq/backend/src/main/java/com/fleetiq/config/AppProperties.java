package com.fleetiq.config;

import java.math.BigDecimal;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app")
public record AppProperties(
        String timezone,
        List<String> corsOrigins,
        Jwt jwt,
        Ml ml,
        Seed seed,
        Topics topics,
        Pricing pricing) {

    public record Jwt(String secret, long expirationMinutes) {}

    public record Ml(String baseUrl, int timeoutMs) {}

    public record Seed(boolean enabled) {}

    public record Topics(String vehicleSearches, String bookingEvents, String notifications) {}

    public record Pricing(
            double surgeThreshold,
            double surgeSlope,
            double maxMultiplier,
            double weekendPremium,
            double taxRate,
            BigDecimal deposit,
            double highRiskDepositFactor,
            BigDecimal lateFeePerHour,
            int graceMinutes,
            int holdMinutes) {}
}
