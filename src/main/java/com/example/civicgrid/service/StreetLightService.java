package com.example.civicgrid.service;

import com.example.civicgrid.entity.StreetLight;
import com.example.civicgrid.entity.Zone;
import com.example.civicgrid.repository.StreetLightRepository;
import com.example.civicgrid.repository.ZoneRepository;
import lombok.Builder;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class StreetLightService {

    private final StreetLightRepository streetLightRepository;
    private final ZoneRepository zoneRepository;

    @Getter
    @Builder
    public static class PowerSummaryDto {
        private long totalLights;
        private long faultyLights;
        private double totalPowerDrawWatts;
        private double averagePowerDrawWatts;
    }

    /**
     * Registers a new street light pole and assigns it to a zone.
     */
    public StreetLight registerLight(String poleCode, Long zoneId, Integer initialDimming) {
        if (poleCode == null || poleCode.trim().isEmpty()) {
            throw new IllegalArgumentException("Pole code cannot be blank.");
        }

        if (streetLightRepository.findByPoleCode(poleCode).isPresent()) {
            throw new IllegalArgumentException("Street light pole already registered with code: " + poleCode);
        }

        Zone zone = zoneRepository.findById(zoneId)
                .orElseThrow(() -> new IllegalArgumentException("Zone not found with id: " + zoneId));

        int dimming = initialDimming != null ? initialDimming : 0;
        validateDimming(dimming);

        StreetLight light = StreetLight.builder()
                .poleCode(poleCode.trim().toUpperCase())
                .zone(zone)
                .dimmingPercentage(dimming)
                .simulatedPowerDraw(0.0)
                .status("ACTIVE")
                .build();

        StreetLight saved = streetLightRepository.save(light);
        log.info("Registered street light pole {} in zone {}", saved.getPoleCode(), zone.getName());
        return saved;
    }

    /**
     * Manually sets the dimming brightness of a light pole (0 - 100%).
     */
    public StreetLight setDimming(Long id, Integer dimmingPercentage) {
        validateDimming(dimmingPercentage);

        StreetLight light = streetLightRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Street light not found with id: " + id));

        light.setDimmingPercentage(dimmingPercentage);
        StreetLight updated = streetLightRepository.save(light);
        log.info("Updated dimming for pole {} (id: {}) to {}%", light.getPoleCode(), id, dimmingPercentage);
        return updated;
    }

    @Transactional(readOnly = true)
    public StreetLight getById(Long id) {
        return streetLightRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Street light not found with id: " + id));
    }

    @Transactional(readOnly = true)
    public List<StreetLight> getAll() {
        return streetLightRepository.findAll();
    }

    @Transactional(readOnly = true)
    public List<StreetLight> getByZone(Long zoneId) {
        return streetLightRepository.findByZoneId(zoneId);
    }

    @Transactional(readOnly = true)
    public PowerSummaryDto getPowerSummary() {
        List<StreetLight> lights = streetLightRepository.findAll();
        long total = lights.size();
        long faulty = lights.stream().filter(l -> "FAULT".equalsIgnoreCase(l.getStatus())).count();
        double totalWatts = lights.stream().mapToDouble(StreetLight::getSimulatedPowerDraw).sum();
        double avgWatts = total > 0 ? (totalWatts / total) : 0.0;

        return PowerSummaryDto.builder()
                .totalLights(total)
                .faultyLights(faulty)
                .totalPowerDrawWatts(Math.round(totalWatts * 100.0) / 100.0)
                .averagePowerDrawWatts(Math.round(avgWatts * 100.0) / 100.0)
                .build();
    }

    private void validateDimming(Integer dimming) {
        if (dimming == null || dimming < 0 || dimming > 100) {
            throw new IllegalArgumentException("Dimming percentage must be between 0 and 100.");
        }
    }
}
