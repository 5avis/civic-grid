package com.example.civicgrid;

import com.example.civicgrid.entity.FaultTicket;
import com.example.civicgrid.entity.StreetLight;
import com.example.civicgrid.entity.TicketStatus;
import com.example.civicgrid.entity.Zone;
import com.example.civicgrid.repository.FaultTicketRepository;
import com.example.civicgrid.repository.StreetLightRepository;
import com.example.civicgrid.repository.ZoneRepository;
import com.example.civicgrid.service.PowerSimulationService;
import com.example.civicgrid.service.StreetLightService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class StreetLightAndFaultDetectionTests {

    @Autowired
    private StreetLightService streetLightService;

    @Autowired
    private PowerSimulationService powerSimulationService;

    @Autowired
    private StreetLightRepository streetLightRepository;

    @Autowired
    private FaultTicketRepository faultTicketRepository;

    @Autowired
    private ZoneRepository zoneRepository;

    @Test
    void testDimmingValidationAndSetting() {
        Zone zone = zoneRepository.findAll().get(0);
        String poleCode = "DIM-" + System.currentTimeMillis();
        StreetLight light = streetLightService.registerLight(poleCode, zone.getId(), 50);

        assertEquals(50, light.getDimmingPercentage());

        // Update dimming
        StreetLight updated = streetLightService.setDimming(light.getId(), 75);
        assertEquals(75, updated.getDimmingPercentage());

        // Validation: Out of bounds dimming should fail
        assertThrows(IllegalArgumentException.class, () -> streetLightService.setDimming(light.getId(), -5));
        assertThrows(IllegalArgumentException.class, () -> streetLightService.setDimming(light.getId(), 105));
    }

    @Test
    void testPowerSimulationAndFaultTicketCreation() {
        Zone zone = zoneRepository.findAll().get(0);
        String poleCode = "FLT-" + System.currentTimeMillis();
        StreetLight light = streetLightService.registerLight(poleCode, zone.getId(), 80);

        // 1. Force night hours
        powerSimulationService.setForceNightHours(true);
        assertTrue(powerSimulationService.isNightHours());

        // 2. Normal simulation: dimming > 0 draws power
        powerSimulationService.simulatePowerDrawAndDetectFaults();
        StreetLight refreshed = streetLightRepository.findById(light.getId()).orElseThrow();
        assertTrue(refreshed.getSimulatedPowerDraw() > 0.0, "Active light should draw power");

        // 3. Simulate hardware failure: bulb fails at night, power drops to 0 while dimming is 80%
        refreshed.setSimulatedPowerDraw(0.0);
        refreshed.setStatus("FAULT");
        streetLightRepository.save(refreshed);

        // Run simulation cycle to trigger auto fault detection
        powerSimulationService.simulatePowerDrawAndDetectFaults();

        // 4. Assert Fault Ticket is automatically created
        List<FaultTicket> tickets = faultTicketRepository.findByStreetLightId(light.getId());
        assertFalse(tickets.isEmpty(), "Fault ticket should be auto-created");
        FaultTicket ticket = tickets.get(0);
        assertEquals(TicketStatus.OPEN, ticket.getStatus());
        assertTrue(ticket.getDescription().contains(poleCode));

        // 5. Assert no duplicate ticket is created if run again
        powerSimulationService.simulatePowerDrawAndDetectFaults();
        List<FaultTicket> ticketsAfterSecondRun = faultTicketRepository.findByStreetLightId(light.getId());
        assertEquals(1, ticketsAfterSecondRun.size(), "Should not create duplicate open fault tickets");

        // 6. Test resolving fault
        powerSimulationService.resolveFault(light.getId());
        StreetLight resolvedLight = streetLightRepository.findById(light.getId()).orElseThrow();
        assertEquals("ACTIVE", resolvedLight.getStatus());
        FaultTicket resolvedTicket = faultTicketRepository.findById(ticket.getId()).orElseThrow();
        assertEquals(TicketStatus.RESOLVED, resolvedTicket.getStatus());
    }

    @Test
    void testNoFaultWhenDimmingIsZero() {
        Zone zone = zoneRepository.findAll().get(0);
        String poleCode = "OFF-" + System.currentTimeMillis();
        StreetLight light = streetLightService.registerLight(poleCode, zone.getId(), 0);

        powerSimulationService.setForceNightHours(true);
        powerSimulationService.simulatePowerDrawAndDetectFaults();

        StreetLight refreshed = streetLightRepository.findById(light.getId()).orElseThrow();
        assertEquals(0.0, refreshed.getSimulatedPowerDraw());

        // Since dimming is 0 (admin intentionally turned it off), NO fault ticket should be created
        List<FaultTicket> tickets = faultTicketRepository.findByStreetLightId(light.getId());
        assertTrue(tickets.isEmpty(), "No fault ticket should be created when light dimming is intentionally 0");
    }
}
