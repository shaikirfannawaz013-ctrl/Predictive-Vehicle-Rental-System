package com.fleetiq.pricing;

import com.fleetiq.pricing.PricingService.Quote;
import com.fleetiq.user.AuthUser;
import java.time.Instant;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/pricing")
public class PricingController {

    private final PricingService pricing;

    public PricingController(PricingService pricing) {
        this.pricing = pricing;
    }

    @GetMapping("/quote")
    public Quote quote(@RequestParam Long vehicleId,
                       @RequestParam Instant startTime,
                       @RequestParam Instant endTime,
                       @AuthenticationPrincipal AuthUser user) {
        return pricing.quote(vehicleId, startTime, endTime, user.id());
    }
}
