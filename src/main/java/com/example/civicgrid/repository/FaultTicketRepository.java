package com.example.civicgrid.repository;

import com.example.civicgrid.entity.FaultTicket;
import com.example.civicgrid.entity.TicketStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface FaultTicketRepository extends JpaRepository<FaultTicket, Long> {

    Optional<FaultTicket> findByTicketNumber(String ticketNumber);

    List<FaultTicket> findByStreetLightId(Long streetLightId);

    List<FaultTicket> findByStatus(TicketStatus status);

    boolean existsByStreetLightIdAndStatus(Long streetLightId, TicketStatus status);
}
