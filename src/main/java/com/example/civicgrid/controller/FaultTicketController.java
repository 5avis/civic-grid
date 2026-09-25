package com.example.civicgrid.controller;

import com.example.civicgrid.entity.FaultTicket;
import com.example.civicgrid.repository.FaultTicketRepository;
import com.example.civicgrid.service.PowerSimulationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/fault-tickets")
@RequiredArgsConstructor
public class FaultTicketController {

    private final FaultTicketRepository faultTicketRepository;
    private final PowerSimulationService powerSimulationService;

    @GetMapping
    public ResponseEntity<List<FaultTicket>> getAllTickets() {
        return ResponseEntity.ok(faultTicketRepository.findAll());
    }

    @PutMapping("/{id}/resolve")
    public ResponseEntity<Void> resolveTicket(@PathVariable Long id) {
        FaultTicket ticket = faultTicketRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Ticket not found: " + id));
        powerSimulationService.resolveFault(ticket.getStreetLight().getId());
        return ResponseEntity.ok().build();
    }
}
