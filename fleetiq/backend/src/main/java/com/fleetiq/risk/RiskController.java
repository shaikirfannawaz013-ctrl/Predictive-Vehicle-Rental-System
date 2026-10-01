package com.fleetiq.risk;

import com.fleetiq.risk.RiskService.CustomerRiskResponse;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/risk")
@PreAuthorize("hasRole('ADMIN')")
public class RiskController {

    private final RiskService risk;

    public RiskController(RiskService risk) {
        this.risk = risk;
    }

    @GetMapping("/customers")
    public List<CustomerRiskResponse> customers() {
        return risk.customers();
    }
}
