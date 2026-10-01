package com.fleetiq.pricing;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class PricingRulesTest {

    @Test
    void partialDaysRoundUp() {
        Instant start = Instant.parse("2026-10-02T09:00:00Z");
        assertThat(PricingService.rentalDays(start, start.plus(Duration.ofHours(5)))).isEqualTo(1);
        assertThat(PricingService.rentalDays(start, start.plus(Duration.ofHours(24)))).isEqualTo(1);
        assertThat(PricingService.rentalDays(start, start.plus(Duration.ofHours(25)))).isEqualTo(2);
    }
}
