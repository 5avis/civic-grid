package com.example.civicgrid.service;

import com.example.civicgrid.entity.FaultTicket;
import com.example.civicgrid.entity.StreetLight;
import com.example.civicgrid.entity.TicketStatus;
import com.example.civicgrid.repository.FaultTicketRepository;
import com.example.civicgrid.repository.StreetLightRepository;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Random;

@Service
@RequiredArgsConstructor
@Slf4j
public class PowerSimulationService {

    private final StreetLightRepository streetLightRepository;
    private final FaultTicketRepository faultTicketRepository;
    private final Random random = new Random();

    @Getter
    @Setter
    @Value("${civicgrid.simulation.force-night-hours:false}")
    private boolean forceNightHours;

    /**
     * Night hours are defined as 18:00 (6 PM) to 06:00 (6 AM), or when forceNightHours is true.
     */
    public boolean isNightHours() {
        if (forceNightHours) {
            return true;
        }
        LocalTime now = LocalTime.now();
        return now.isAfter(LocalTime.of(18, 0)) || now.isBefore(LocalTime.of(6, 0));
    }

    /**
     * Periodic background job that simulates street light power consumption and creates fault tickets.
     */
    @Scheduled(fixedRateString = "${civicgrid.simulation.rate-ms:5000}")
    @Transactional
    public void runSimulationCycle() {
        simulatePowerDrawAndDetectFaults();
    }

    /**
     * Core simulation and fault detection logic:
     * - If dimming == 0: light is off (0W).
     * - If dimming > 0 and light is active: power draw = (dimming / 100) * 150W (plus realistic minor fluctuation).
     * - IF night hours AND dimming > 0 AND power draw == 0: automatically create a FaultTicket.
     */
    @Transactional
    public void simulatePowerDrawAndDetectFaults() {
        List<StreetLight> lights = streetLightRepository.findAll();
        boolean night = isNightHours();

        for (StreetLight light : lights) {
            int dimming = light.getDimmingPercentage();

            if ("FAULT".equalsIgnoreCase(light.getStatus())) {
                // Faulty light draws 0 watts
                light.setSimulatedPowerDraw(0.0);
            } else if (dimming == 0) {
                // Intentionally switched off by admin
                light.setSimulatedPowerDraw(0.0);
            } else {
                // Normal simulated power draw (nominal 150W max rating)
                double baseWatts = (dimming / 100.0) * 150.0;
                double jitter = (random.nextDouble() - 0.5) * 2.0; // +/- 1W fluctuation
                double power = Math.max(1.0, Math.round((baseWatts + jitter) * 100.0) / 100.0);
                light.setSimulatedPowerDraw(power);
            }

            streetLightRepository.save(light);

            // Fault Ticket Trigger Condition:
            // IF it is night hours AND light's dimming > 0% (should be on) AND simulated power draw = 0
            if (night && dimming > 0 && light.getSimulatedPowerDraw() == 0.0) {
                boolean hasOpenTicket = faultTicketRepository.existsByStreetLightIdAndStatus(light.getId(), TicketStatus.OPEN);
                if (!hasOpenTicket) {
                    String ticketNumber = "FLT-" + System.currentTimeMillis();
                    String description = String.format(
                            "Light pole %s in %s has dimming set to %d%% during night hours but simulated power draw is 0.0W.",
                            light.getPoleCode(), light.getZone().getName(), dimming
                    );

                    FaultTicket ticket = FaultTicket.builder()
                            .ticketNumber(ticketNumber)
                            .streetLight(light)
                            .description(description)
                            .createdAt(LocalDateTime.now())
                            .status(TicketStatus.OPEN)
                            .build();

                    faultTicketRepository.save(ticket);
                    light.setStatus("FAULT");
                    streetLightRepository.save(light);

                    log.warn("AUTO-FAULT DETECTED: Created fault ticket {} for pole {}", ticketNumber, light.getPoleCode());
                }
            }
        }
    }

    /**
     * Helper to simulate a hardware bulb failure on a light pole (sets power draw to 0W while dimmed).
     */
    @Transactional
    public void triggerHardwareFault(Long streetLightId) {
        StreetLight light = streetLightRepository.findById(streetLightId)
                .orElseThrow(() -> new IllegalArgumentException("Street light not found: " + streetLightId));
        light.setSimulatedPowerDraw(0.0);
        light.setStatus("FAULT");
        streetLightRepository.save(light);
    }

    /**
     * Resolves open tickets for a street light and restores it to ACTIVE status.
     */
    @Transactional
    public void resolveFault(Long streetLightId) {
        StreetLight light = streetLightRepository.findById(streetLightId)
                .orElseThrow(() -> new IllegalArgumentException("Street light not found: " + streetLightId));

        List<FaultTicket> openTickets = faultTicketRepository.findByStreetLightId(streetLightId);
        for (FaultTicket ticket : openTickets) {
            if (ticket.getStatus() == TicketStatus.OPEN) {
                ticket.setStatus(TicketStatus.RESOLVED);
                faultTicketRepository.save(ticket);
            }
        }

        light.setStatus("ACTIVE");
        streetLightRepository.save(light);
        log.info("Resolved faults for street light pole {}", light.getPoleCode());
    }
}
