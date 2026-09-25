package com.example.civicgrid.repository;

import com.example.civicgrid.entity.StreetLight;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface StreetLightRepository extends JpaRepository<StreetLight, Long> {

    Optional<StreetLight> findByPoleCode(String poleCode);

    List<StreetLight> findByZoneId(Long zoneId);

    @Query("SELECT COALESCE(SUM(s.simulatedPowerDraw), 0.0) FROM StreetLight s")
    Double sumSimulatedPowerDraw();

    @Query("SELECT COUNT(s) FROM StreetLight s WHERE s.status = 'FAULT'")
    Long countFaultyLights();
}
