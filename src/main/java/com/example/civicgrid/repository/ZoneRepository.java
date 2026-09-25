package com.example.civicgrid.repository;

import com.example.civicgrid.entity.Zone;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ZoneRepository extends JpaRepository<Zone, Long> {
    Optional<Zone> findByName(String name);
}
