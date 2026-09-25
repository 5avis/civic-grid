package com.example.civicgrid.controller;

import com.example.civicgrid.dto.RegisterLightRequest;
import com.example.civicgrid.dto.SetDimmingRequest;
import com.example.civicgrid.entity.StreetLight;
import com.example.civicgrid.service.StreetLightService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/street-lights")
@RequiredArgsConstructor
public class StreetLightController {

    private final StreetLightService streetLightService;

    /**
     * API 1: Register a new light pole (assign to a zone).
     * POST /api/street-lights
     */
    @PostMapping
    public ResponseEntity<StreetLight> registerLight(@Valid @RequestBody RegisterLightRequest request) {
        StreetLight registered = streetLightService.registerLight(
                request.getPoleCode(),
                request.getZoneId(),
                request.getDimmingPercentage()
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(registered);
    }

    /**
     * API 2: Manually set a light's brightness (0 - 100%).
     * PUT /api/street-lights/{id}/dimming
     */
    @PutMapping("/{id}/dimming")
    public ResponseEntity<StreetLight> setDimming(
            @PathVariable Long id,
            @Valid @RequestBody SetDimmingRequest request
    ) {
        StreetLight updated = streetLightService.setDimming(id, request.getDimmingPercentage());
        return ResponseEntity.ok(updated);
    }

    /**
     * API 3: Return total power usage summary.
     * GET /api/street-lights/power-summary
     */
    @GetMapping("/power-summary")
    public ResponseEntity<StreetLightService.PowerSummaryDto> getPowerSummary() {
        StreetLightService.PowerSummaryDto summary = streetLightService.getPowerSummary();
        return ResponseEntity.ok(summary);
    }

    /**
     * Return all registered street lights for the desktop UI.
     * GET /api/street-lights
     */
    @GetMapping
    public ResponseEntity<java.util.List<StreetLight>> getAllLights() {
        return ResponseEntity.ok(streetLightService.getAll());
    }
}
