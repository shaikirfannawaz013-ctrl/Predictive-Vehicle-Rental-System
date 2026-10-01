package com.fleetiq.allocation;

import com.fleetiq.allocation.AllocationService.ApplyResponse;
import com.fleetiq.allocation.AllocationService.MoveResponse;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/allocation")
@PreAuthorize("hasRole('ADMIN')")
public class AllocationController {

    private final AllocationService allocation;

    public AllocationController(AllocationService allocation) {
        this.allocation = allocation;
    }

    @GetMapping("/recommendations")
    public List<MoveResponse> recommendations() {
        return allocation.recommendations();
    }

    @PostMapping("/{id}/apply")
    public ApplyResponse apply(@PathVariable Long id) {
        return allocation.apply(id);
    }
}
