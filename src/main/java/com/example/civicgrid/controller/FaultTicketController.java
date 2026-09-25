package com.example.civicgrid.controller;

import com.example.civicgrid.entity.FaultTicket;
import com.example.civicgrid.entity.StreetLight;
import com.example.civicgrid.entity.TicketStatus;
import com.example.civicgrid.repository.FaultTicketRepository;
import com.example.civicgrid.repository.StreetLightRepository;
import com.example.civicgrid.service.PowerSimulationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/fault-tickets")
@RequiredArgsConstructor
public class FaultTicketController {

    private final FaultTicketRepository faultTicketRepository;
    private final StreetLightRepository streetLightRepository;
    private final PowerSimulationService powerSimulationService;

    @GetMapping
    public ResponseEntity<List<FaultTicket>> getAllTickets() {
        return ResponseEntity.ok(faultTicketRepository.findAll());
    }

    @PutMapping("/{id}/resolve")
    public ResponseEntity<?> resolveTicket(
            @PathVariable Long id,
            @RequestHeader(value = "X-User-Role", required = false) String roleHeader,
            @RequestParam(value = "role", required = false) String roleParam
    ) {
        String role = roleHeader != null ? roleHeader : roleParam;
        if (role != null && !"ADMIN".equalsIgnoreCase(role)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "Access denied: Only Admin can resolve fault tickets."));
        }
        FaultTicket ticket = faultTicketRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Ticket not found: " + id));
        powerSimulationService.resolveFault(ticket.getStreetLight().getId());
        return ResponseEntity.ok().build();
    }

    @PutMapping("/{id}/toggle")
    public ResponseEntity<?> toggleTicketStatus(
            @PathVariable Long id,
            @RequestHeader(value = "X-User-Role", required = false) String roleHeader,
            @RequestParam(value = "role", required = false) String roleParam
    ) {
        String role = roleHeader != null ? roleHeader : roleParam;
        if (role != null && !"ADMIN".equalsIgnoreCase(role)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "Access denied: Only Admin can change fault ticket resolution status."));
        }

        FaultTicket ticket = faultTicketRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Ticket not found: " + id));

        StreetLight light = ticket.getStreetLight();

        if (ticket.getStatus() == TicketStatus.RESOLVED) {
            // Switch from RESOLVED -> NOT RESOLVED (OPEN)
            ticket.setStatus(TicketStatus.OPEN);
            faultTicketRepository.save(ticket);
            if (light != null) {
                light.setStatus("FAULT");
                streetLightRepository.save(light);
            }
        } else {
            // Switch from NOT RESOLVED (OPEN) -> RESOLVED
            ticket.setStatus(TicketStatus.RESOLVED);
            faultTicketRepository.save(ticket);
            if (light != null) {
                boolean hasOtherOpen = faultTicketRepository.findByStreetLightId(light.getId())
                        .stream()
                        .anyMatch(t -> !t.getId().equals(id) && t.getStatus() == TicketStatus.OPEN);
                if (!hasOtherOpen) {
                    light.setStatus("ACTIVE");
                    streetLightRepository.save(light);
                }
            }
        }

        return ResponseEntity.ok(ticket);
    }
}
